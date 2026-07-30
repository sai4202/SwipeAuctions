package com.swipeauctions.admin.controller;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.catalog.controller.CatalogController;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.entity.ListingImage;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.storage.service.StorageProvider;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.platform.PlatformAccountService;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin "+ Add Stock" flow: create a single item, or bulk-import many from an Excel file. Every
 * item created here is attributed to the platform "Swipe Stock" seller account (admins don't have
 * a User identity of their own to be a seller under) — the per-item {@code swipeStock} flag is what
 * actually controls whether it shows on the /swipe-stock page; everything created here still shows
 * on the normal /auctions browse regardless of that flag.
 */
@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    /**
     * The structured item-detail fields shown as tabs (General Details / Registration / Insurance /
     * Other Details / Remarks) on the item detail page — same 21 fields, same order, as
     * {@code frontend/src/detailFields.ts}, which is the source of truth for the single-item Add
     * Stock form. Kept in sync manually (TS and Java can't literally share this list) — a column
     * header here is exactly the sheet header a bulk-uploader fills in; the second element is the
     * {@code ListingAttribute} key it's stored under, exactly matching {@code DetailFieldDef.key}.
     */
    private static final String[][] DETAIL_COLUMNS = {
            {"Power Steering", "powerSteering"}, {"Yard Name", "yardName"}, {"Yard Location", "yardLocation"},
            {"Payment Terms", "paymentTerms"}, {"RC Book Available", "rcBookAvailable"},
            {"Seller Reference", "sellerReference"}, {"Sun Roof", "sunRoof"},
            {"CTE Contact Person", "cteContactPerson"}, {"CTE Contact Person Phone", "cteContactPersonPhone"},
            {"Registration Number", "registrationNumber"}, {"Year of Manufacturing", "yearOfManufacturing"},
            {"Insurance Provider", "insuranceProvider"}, {"Insurance Valid Upto", "insuranceValidUpto"},
            {"Hypothecation", "hypothecation"}, {"Has Loan Been Paid Off", "loanPaidOff"},
            {"Whether Valid Form 35 NOC Available", "form35NocAvailable"}, {"Listing Remarks", "listingRemarks"},
            {"Faremeter", "faremeter"}, {"Chassis No", "chassisNo"}, {"Engine No", "engineNo"},
            {"Repo Date", "repoDate"}, {"Parking Rate (per day)", "parkingRatePerDay"},
            {"Additional Remarks", "additionalRemarks"},
    };

    private final CatalogService catalogService;
    private final AuctionService auctionService;
    private final StorageProvider storageProvider;
    private final PlatformAccountService platformAccountService;

    /**
     * Category names (case-insensitive) treated as "vehicles" for {@link #requireVehicleDetails} —
     * same fixed-name-set convention {@code frontend/src/eventCategories.ts} already uses for
     * "which categories get the events-first browsing flow", not a DB-driven flag. Kept here rather
     * than on {@link Category} itself since only admin-created stock enforces this (a regular
     * seller's own listing flow — {@code CatalogController}/{@code CatalogService.createListing} —
     * was never extended with these fields and has no form to satisfy the requirement).
     */
    private static final java.util.Set<String> VEHICLE_CATEGORY_NAMES =
            java.util.Set.of("vehicles", "bank vehicles", "auto", "insurance");

    /**
     * The events browse UI's Vehicle Type filter reads this exact attribute key (see
     * {@code frontend/src/catalogFilters.ts}'s VEHICLE_TYPE_KEY) — shown for the same category set as
     * {@code frontend/src/eventCategories.ts}'s EVENT_CATEGORIES (Bank Vehicles/Insurance/Premium/Auto)
     * on the admin form, but accepted here for any category rather than re-deriving that set backend-
     * side too. A real bank/insurer/fleet auction never mixes vehicle types under one listing — see
     * {@link com.swipeauctions.common.dev.DevDataSeeder} for the same rule applied to demo data.
     */
    private static final String VEHICLE_TYPE_ATTR_KEY = "Vehicle Type";
    private static final java.util.Set<String> VEHICLE_TYPE_VALUES =
            java.util.Set.of("4W", "CV", "2W", "TR/FE", "3W", "CE");

    // ---- Single item ----

    @PostMapping("/listings")
    public StockListingResponse createListing(@Valid @RequestBody CreateStockListingRequest req) {
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        Category category = resolveCategory(req.categoryId(), req.categoryName());
        requireVehicleDetails(category, req.condition(), req.attributes());
        validateVehicleType(req.attributes());
        Listing listing = catalogService.createListing(seller, category.getId(), req.title(), req.description(),
                req.brand(), req.condition(), req.city(), req.state(), req.zip(), req.reservePrice(),
                req.attributes(), Boolean.TRUE.equals(req.swipeStock()),
                req.requiredTier() != null ? req.requiredTier() : SubscriptionTier.NONE);
        return toStockListing(listing);
    }

    @PostMapping(value = "/listings/{id}/images", consumes = "multipart/form-data")
    public CatalogController.ImageResponse addImage(@PathVariable UUID id, @RequestParam MultipartFile file,
                                                     @RequestParam(defaultValue = "false") boolean cover) {
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        String url = storageProvider.store(file, "listings/" + id);
        ListingImage image = catalogService.addImage(seller, id, url, cover);
        return new CatalogController.ImageResponse(image.getId(), image.getUrl(), image.isCover());
    }

    @PostMapping("/listings/{id}/auction")
    public StockAuctionResponse createAuction(@PathVariable UUID id, @Valid @RequestBody CreateStockAuctionRequest req) {
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        LocalDateTime start = req.startTime() != null ? req.startTime() : LocalDateTime.now();
        LocalDateTime end = req.endTime() != null ? req.endTime() : start.plusDays(3);
        Auction a = auctionService.createAuction(seller, id, req.basePrice(), start, end, req.eventId());
        return new StockAuctionResponse(a.getId(), a.getListing().getId(), a.getStatus().name());
    }

    // ---- Bulk (Excel) ----

    /**
     * One row per item. Header row required (case-insensitive, any column order): Title, Category,
     * Brand, Condition, City, State, Zip, Base Price, Start Time, End Time, Swipe Stock, Vehicle Type,
     * plus any of the {@link #DETAIL_COLUMNS} (Yard Name, Registration Number, Chassis No, ...) —
     * those populate the item's detail-page tabs exactly like the single-item Add Stock form does.
     * Only Title, Category and Base Price are required — everything else has a sensible default, and
     * every other column is optional (blank cells are simply omitted, same as leaving a form field
     * empty). A row is one vehicle type, never a mix — see {@link #VEHICLE_TYPE_ATTR_KEY}. A row-level
     * error doesn't abort the batch; every other valid row is still imported.
     */
    @PostMapping(value = "/bulk", consumes = "multipart/form-data")
    public BulkImportResponse bulkImport(@RequestParam MultipartFile file,
                                          @RequestParam(defaultValue = "false") boolean swipeStock) {
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        List<RowError> errors = new ArrayList<>();
        int created = 0;
        int totalRows = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new BadRequestException("Sheet has no header row");
            }
            Map<String, Integer> cols = new LinkedHashMap<>();
            for (Cell c : header) {
                String key = cellString(c);
                if (key != null && !key.isBlank()) cols.put(key.trim().toLowerCase(), c.getColumnIndex());
            }
            requireColumn(cols, "title");
            requireColumn(cols, "category");
            requireColumn(cols, "base price");

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) continue;
                totalRows++;
                int rowNumber = r + 1; // 1-based, matches what a spreadsheet user sees
                try {
                    String title = requireCell(row, cols, "title", rowNumber, "Title");
                    String categoryName = requireCell(row, cols, "category", rowNumber, "Category");
                    BigDecimal price = requirePrice(row, cols, rowNumber);

                    String brand = optionalString(row, cols, "brand");
                    ItemCondition condition = parseCondition(optionalString(row, cols, "condition"));
                    String city = optionalString(row, cols, "city");
                    String state = optionalString(row, cols, "state");
                    String zip = optionalString(row, cols, "zip");
                    LocalDateTime start = cellDateTime(row, cols, "start time");
                    LocalDateTime end = cellDateTime(row, cols, "end time");
                    if (start == null) start = LocalDateTime.now();
                    if (end == null) end = start.plusDays(3);
                    Boolean rowSwipeStock = cellBoolean(row, cols, "swipe stock");
                    boolean effectiveSwipeStock = rowSwipeStock != null ? rowSwipeStock : swipeStock;

                    Map<String, String> attributes = new LinkedHashMap<>();
                    for (String[] col : DETAIL_COLUMNS) {
                        String value = optionalString(row, cols, col[0].toLowerCase());
                        if (value != null) attributes.put(col[1], value);
                    }
                    String vehicleType = optionalString(row, cols, "vehicle type");
                    if (vehicleType != null) attributes.put(VEHICLE_TYPE_ATTR_KEY, vehicleType);

                    Category category = catalogService.resolveOrCreateCategory(categoryName);
                    requireVehicleDetails(category, condition, attributes);
                    validateVehicleType(attributes);
                    Listing listing = catalogService.createListing(seller, category.getId(), title,
                            title + " — bulk-imported via admin Add Stock.", brand, condition, city, state, zip,
                            price, attributes, effectiveSwipeStock);
                    auctionService.createAuction(seller, listing.getId(), price, start, end, null);
                    created++;
                } catch (Exception e) {
                    errors.add(new RowError(rowNumber, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file — is it a valid .xlsx/.xls?");
        }

        return new BulkImportResponse(totalRows, created, errors);
    }

    private static final String[] BASE_HEADERS = {"Title", "Category", "Brand", "Condition", "City", "State", "Zip",
            "Base Price", "Start Time", "End Time", "Swipe Stock", "Vehicle Type"};

    /**
     * A ready-to-fill .xlsx with the exact header row the bulk importer expects (base columns plus
     * every {@link #DETAIL_COLUMNS} entry), plus a few example rows spanning different
     * categories/field combinations — every detail column is optional, so the examples deliberately
     * leave some blank per row rather than filling all 23 on every line, showing that's expected.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock");
            List<String> headers = new ArrayList<>(List.of(BASE_HEADERS));
            for (String[] col : DETAIL_COLUMNS) headers.add(col[0]);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) headerRow.createCell(i).setCellValue(headers.get(i));

            for (int i = 0; i < EXAMPLE_ROWS.length; i++) {
                Row row = sheet.createRow(i + 1);
                String[] values = EXAMPLE_ROWS[i];
                for (int col = 0; col < values.length; col++) {
                    if (!values[col].isEmpty()) row.createCell(col).setCellValue(values[col]);
                }
            }

            for (int i = 0; i < headers.size(); i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=swipe-stock-template.xlsx")
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sample rows for the template, column-for-column aligned to BASE_HEADERS + DETAIL_COLUMNS.
     * Deliberately varied: a fully-detailed bank-repo vehicle, a lighter insurance-salvage item, and
     * a plain electronics item using none of the detail columns at all — showing the range from "fill
     * in everything" to "ignore the detail columns entirely." Both vehicle-category rows still carry
     * Registration Number/Chassis No/Yard Name/Yard Location since requireVehicleDetails makes those
     * 4 mandatory for any non-NEW item in a vehicle category (Vehicles/Bank Vehicles/Auto/Insurance),
     * and a Vehicle Type — a single row is always one vehicle type, never a mix (see
     * {@link #VEHICLE_TYPE_ATTR_KEY}); Electronics leaves it blank since it isn't a vehicle category.
     */
    private static final String[][] EXAMPLE_ROWS = {
            // Title, Category, Brand, Condition, City, State, Zip, Base Price, Start, End, Swipe Stock, Vehicle Type,
            // Power Steering, Yard Name, Yard Location, Payment Terms, RC Book Available, Seller Reference,
            // Sun Roof, CTE Contact Person, CTE Contact Person Phone, Registration Number, Year of Manufacturing,
            // Insurance Provider, Insurance Valid Upto, Hypothecation, Has Loan Been Paid Off,
            // Whether Valid Form 35 NOC Available, Listing Remarks, Faremeter, Chassis No, Engine No,
            // Repo Date, Parking Rate (per day), Additional Remarks
            {"Mahindra Bolero Pickup (Repo)", "Bank Vehicles", "Mahindra", "USED", "Madanapalle", "Andhra Pradesh",
                    "517325", "130200", "2026-08-01 10:00", "2026-08-05 18:00", "FALSE", "CV",
                    "No", "Shriram Yard Bengaluru", "Shriram Yard Bengaluru, Survey No 52/1A", "Payment to be made within 24 hours from the time of approval",
                    "No", "L2ATHI10845213", "No", "Tulsi B", "9892803643", "AP02Y8911", "2015",
                    "", "", "No", "No", "No", "L2A_THI10845213", "No", "MD2B77AX3PWA26654", "PFXWPA18287",
                    "2026-07-17", "100", "Bids once placed cannot be cancelled. Parking charges to be paid by buyer as per seller terms."},
            {"Water-Damaged Hyundai Creta (Salvage)", "Insurance", "Hyundai", "FOR_PARTS", "Chennai", "Tamil Nadu",
                    "600001", "260000", "2026-08-02 09:00", "2026-08-06 18:00", "FALSE", "4W",
                    "", "IDBI Yard Chennai", "IDBI Yard Chennai, Guindy Industrial Estate", "", "", "", "", "", "",
                    "TN09CD5678", "2021",
                    "ICICI Lombard", "2027-03-31", "", "", "", "", "", "MA3ETEB1S00123456", "",
                    "", "", "Flood-damaged; sold as-is for parts/scrap only."},
            {"Sealed Dell Laptop (Grade A)", "Electronics", "Dell", "NEW", "Hyderabad", "Telangana",
                    "500001", "45000", "", "", "TRUE", "",
                    "", "", "", "", "", "", "", "", "", "", "",
                    "", "", "", "", "", "", "", "", "",
                    "", "", ""},
    };

    // ---- helpers ----

    /**
     * Every real-world used/repossessed vehicle listing has a registration number, a chassis number,
     * and a yard it's physically sitting in — unlike the rest of {@link #DETAIL_COLUMNS}, these 4
     * aren't optional extras for that case. A brand-new (unregistered, not-yet-repossessed) vehicle,
     * or any non-vehicle category (Electronics, Properties, ...), is exempt.
     */
    private static void requireVehicleDetails(Category category, ItemCondition condition, Map<String, String> attributes) {
        boolean isVehicleCategory = VEHICLE_CATEGORY_NAMES.contains(category.getName().trim().toLowerCase());
        boolean isNew = condition == ItemCondition.NEW;
        if (!isVehicleCategory || isNew) return;

        List<String> missing = new ArrayList<>();
        for (String[] pair : new String[][]{{"registrationNumber", "Registration Number"}, {"chassisNo", "Chassis No"},
                {"yardName", "Yard Name"}, {"yardLocation", "Yard Location"}}) {
            String value = attributes == null ? null : attributes.get(pair[0]);
            if (value == null || value.isBlank()) missing.add(pair[1]);
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException(String.join(", ", missing)
                    + (missing.size() > 1 ? " are required" : " is required")
                    + " for a used " + category.getName() + " item (only exempt for Condition = NEW)");
        }
    }

    /** If a Vehicle Type was given, it must be one of the fixed values the events browse filter uses. */
    private static void validateVehicleType(Map<String, String> attributes) {
        String value = attributes == null ? null : attributes.get(VEHICLE_TYPE_ATTR_KEY);
        if (value != null && !VEHICLE_TYPE_VALUES.contains(value)) {
            throw new BadRequestException("Vehicle Type must be one of " + VEHICLE_TYPE_VALUES + ", got \"" + value + "\"");
        }
    }

    private Category resolveCategory(UUID categoryId, String categoryName) {
        if (categoryId != null) {
            return catalogService.listCategories().stream()
                    .filter(c -> c.getId().equals(categoryId)).findFirst()
                    .orElseThrow(() -> new BadRequestException("Category not found"));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            return catalogService.resolveOrCreateCategory(categoryName);
        }
        throw new BadRequestException("categoryId or categoryName is required");
    }

    private static void requireColumn(Map<String, Integer> cols, String name) {
        if (!cols.containsKey(name)) {
            throw new BadRequestException("Missing required column: " + name);
        }
    }

    private static boolean isBlankRow(Row row) {
        for (Cell c : row) {
            if (cellString(c) != null && !cellString(c).isBlank()) return false;
        }
        return true;
    }

    private static String requireCell(Row row, Map<String, Integer> cols, String col, int rowNumber, String label) {
        String v = optionalString(row, cols, col);
        if (v == null || v.isBlank()) throw new BadRequestException(label + " is required (row " + rowNumber + ")");
        return v;
    }

    private static BigDecimal requirePrice(Row row, Map<String, Integer> cols, int rowNumber) {
        Integer idx = cols.get("base price");
        Cell cell = idx == null ? null : row.getCell(idx);
        if (cell == null) throw new BadRequestException("Base Price is required (row " + rowNumber + ")");
        try {
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            return new BigDecimal(cellString(cell).trim());
        } catch (Exception e) {
            throw new BadRequestException("Base Price is not a valid number (row " + rowNumber + ")");
        }
    }

    private static String optionalString(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        String s = cellString(cell);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ItemCondition parseCondition(String raw) {
        if (raw == null) return ItemCondition.USED;
        try {
            return ItemCondition.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return ItemCondition.USED;
        }
    }

    private static Boolean cellBoolean(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue();
        String s = cellString(cell);
        if (s == null || s.isBlank()) return null;
        s = s.trim().toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("1");
    }

    private static LocalDateTime cellDateTime(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String s = cellString(cell);
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return s.trim().length() <= 10
                        ? java.time.LocalDate.parse(s.trim(), fmt.withResolverStyle(java.time.format.ResolverStyle.SMART)).atStartOfDay()
                        : LocalDateTime.parse(s.trim(), fmt);
            } catch (Exception ignored) { /* try next pattern */ }
        }
        return null;
    }

    private static String cellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private static StockListingResponse toStockListing(Listing l) {
        return new StockListingResponse(l.getId(), l.getTitle(), l.getCategory().getId(),
                l.getCategory().getName(), l.isSwipeStock(), l.getRequiredTier());
    }

    public record CreateStockListingRequest(
            @NotBlank String title,
            String description,
            UUID categoryId,
            String categoryName,
            String brand,
            ItemCondition condition,
            String city,
            String state,
            String zip,
            @NotNull BigDecimal reservePrice,
            Map<String, String> attributes,
            Boolean swipeStock,
            SubscriptionTier requiredTier) {}

    public record StockListingResponse(UUID id, String title, UUID categoryId, String categoryName, boolean swipeStock,
                                       SubscriptionTier requiredTier) {}

    public record CreateStockAuctionRequest(BigDecimal basePrice, LocalDateTime startTime, LocalDateTime endTime, UUID eventId) {}

    public record StockAuctionResponse(UUID auctionId, UUID listingId, String status) {}

    public record RowError(int row, String message) {}

    public record BulkImportResponse(int totalRows, int created, List<RowError> errors) {}
}
