package com.swipeauctions.admin.controller;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.service.AdminAuditLogService;
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
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
     * Other Details / Remarks) on the item detail page — same 23 fields, same order, as
     * {@code frontend/src/detailFields.ts}, which is the source of truth for the single-item Add
     * Stock form. Kept in sync manually (TS and Java can't literally share this list). Each entry is
     * {header, ListingAttribute key, tab} — the header is what a bulk-uploader sees (either as its
     * own column, for a mandatory/Remarks field, or as the label half of a "Label: value" line inside
     * its tab's collapsed column — see {@link #COLLAPSIBLE_TABS}/{@link #parseDetailListCell}).
     */
    private static final String[][] DETAIL_COLUMNS = {
            {"Power Steering", "powerSteering", "General Details"}, {"Yard Name", "yardName", "General Details"},
            {"Yard Location", "yardLocation", "General Details"}, {"Payment Terms", "paymentTerms", "General Details"},
            {"RC Book Available", "rcBookAvailable", "General Details"},
            {"Seller Reference", "sellerReference", "General Details"}, {"Sun Roof", "sunRoof", "General Details"},
            {"CTE Contact Person", "cteContactPerson", "General Details"},
            {"CTE Contact Person Phone", "cteContactPersonPhone", "General Details"},
            {"Registration Number", "registrationNumber", "Registration"},
            {"Year of Manufacturing", "yearOfManufacturing", "Registration"},
            {"Insurance Provider", "insuranceProvider", "Insurance"}, {"Insurance Valid Upto", "insuranceValidUpto", "Insurance"},
            {"Hypothecation", "hypothecation", "Other Details"}, {"Has Loan Been Paid Off", "loanPaidOff", "Other Details"},
            {"Whether Valid Form 35 NOC Available", "form35NocAvailable", "Other Details"},
            {"Listing Remarks", "listingRemarks", "Other Details"}, {"Faremeter", "faremeter", "Other Details"},
            {"Chassis No", "chassisNo", "Other Details"}, {"Engine No", "engineNo", "Other Details"},
            {"Repo Date", "repoDate", "Remarks"}, {"Parking Rate (per day)", "parkingRatePerDay", "Remarks"},
            {"Additional Remarks", "additionalRemarks", "Remarks"},
    };

    /** Every real used/repossessed vehicle has a registration, chassis, and yard — see
     *  {@link #requireVehicleDetails}. These 4 keys keep their own dedicated column/input rather than
     *  folding into their tab's collapsed "Label: value" list, mirroring
     *  {@code frontend/src/detailFields.ts}'s {@code REQUIRED_FOR_USED_VEHICLES}. A {@code List}, not
     *  a {@code Set} — order matters here (deterministic missing-fields error message / column order),
     *  and {@code Set.of()} doesn't guarantee iteration order. */
    private static final java.util.List<String> MANDATORY_DETAIL_KEYS =
            java.util.List.of("registrationNumber", "chassisNo", "yardName", "yardLocation");

    /**
     * {tab, Excel column header} for the tabs whose non-mandatory fields are entered as a single
     * "Label: value per line" column instead of one column per field — mirrors
     * {@code frontend/src/detailFields.ts}'s {@code COLLAPSIBLE_TABS}. Remarks is left out: no
     * mandatory fields, already mostly free-text.
     */
    private static final String[][] COLLAPSED_COLUMNS = {
            {"General Details", "General Details (Label: Value, one per line)"},
            {"Registration", "Registration (Label: Value, one per line)"},
            {"Insurance", "Insurance (Label: Value, one per line)"},
            {"Other Details", "Other Details (Label: Value, one per line)"},
    };

    private final CatalogService catalogService;
    private final AuctionService auctionService;
    private final StorageProvider storageProvider;
    private final PlatformAccountService platformAccountService;
    private final LoggedInUserUtil loggedInUserUtil;
    private final AdminAuditLogService auditLogService;

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
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        Category category = resolveCategory(req.categoryId(), req.categoryName());
        requireVehicleDetails(category, req.condition(), req.attributes());
        validateVehicleType(req.attributes());
        Listing listing = catalogService.createListing(seller, category.getId(), req.title(), req.description(),
                req.brand(), req.condition(), req.city(), req.state(), req.zip(), req.reservePrice(),
                req.attributes(), Boolean.TRUE.equals(req.swipeStock()),
                req.requiredTier() != null ? req.requiredTier() : SubscriptionTier.NONE);
        auditLogService.record(admin, AuditAction.STOCK_LISTING_CREATED, "Listing", listing.getId().toString(),
                "Created stock listing \"" + listing.getTitle() + "\"");
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
     * the 4 {@link #MANDATORY_DETAIL_KEYS} columns (Registration Number, Chassis No, Yard Name, Yard
     * Location), one combined "Label: Value per line" column per {@link #COLLAPSIBLE_TABS} tab (see
     * {@link #COLLAPSED_COLUMNS}/{@link #parseDetailListCell}), and Remarks' 3 individual columns —
     * together these populate the item's detail-page tabs exactly like the single-item Add Stock form
     * does. Only Title, Category and Base Price are required — everything else has a sensible
     * default, and every other column is optional (blank cells are simply omitted, same as leaving a
     * form field empty). A row is one vehicle type, never a mix — see {@link #VEHICLE_TYPE_ATTR_KEY}.
     * A row-level error doesn't abort the batch; every other valid row is still imported.
     */
    @PostMapping(value = "/bulk", consumes = "multipart/form-data")
    public BulkImportResponse bulkImport(@RequestParam MultipartFile file,
                                          @RequestParam(defaultValue = "false") boolean swipeStock) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
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
                    for (String key : MANDATORY_DETAIL_KEYS) {
                        String value = optionalString(row, cols, detailColumnLabel(key).toLowerCase());
                        if (value != null) attributes.put(key, value);
                    }
                    for (String[] tabCol : COLLAPSED_COLUMNS) {
                        String value = optionalString(row, cols, tabCol[1].toLowerCase());
                        if (value != null) attributes.putAll(parseDetailListCell(tabCol[0], value));
                    }
                    for (String[] col : DETAIL_COLUMNS) {
                        if (!"Remarks".equals(col[2])) continue;
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

        auditLogService.record(admin, AuditAction.STOCK_BULK_IMPORTED, "Stock", null,
                "Bulk-imported stock: " + created + " of " + totalRows + " rows created" + (errors.isEmpty() ? "" : ", " + errors.size() + " error(s)"));
        return new BulkImportResponse(totalRows, created, errors);
    }

    private static final String[] BASE_HEADERS = {"Title", "Category", "Brand", "Condition", "City", "State", "Zip",
            "Base Price", "Start Time", "End Time", "Swipe Stock", "Vehicle Type"};

    /**
     * A ready-to-fill .xlsx with the exact header row the bulk importer expects (base columns, the 4
     * mandatory detail columns, one combined "Label: Value per line" column per
     * {@link #COLLAPSED_COLUMNS} tab, and Remarks' 3 columns — 23 total, down from a flat 35 when
     * every one of the 23 {@link #DETAIL_COLUMNS} had its own column), plus a few example rows
     * spanning different categories/field combinations.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock");
            List<String> headers = new ArrayList<>(List.of(BASE_HEADERS));
            for (String key : MANDATORY_DETAIL_KEYS) headers.add(detailColumnLabel(key));
            for (String[] tabCol : COLLAPSED_COLUMNS) headers.add(tabCol[1]);
            for (String[] col : DETAIL_COLUMNS) {
                if ("Remarks".equals(col[2])) headers.add(col[0]);
            }

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) headerRow.createCell(i).setCellValue(headers.get(i));

            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);

            for (int i = 0; i < EXAMPLE_ROWS.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.setHeightInPoints(60);
                String[] values = EXAMPLE_ROWS[i];
                for (int col = 0; col < values.length; col++) {
                    if (values[col].isEmpty()) continue;
                    Cell cell = row.createCell(col);
                    cell.setCellValue(values[col]);
                    if (values[col].contains("\n")) cell.setCellStyle(wrapStyle);
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
     * Sample rows for the template, column-for-column aligned to BASE_HEADERS + the 4 mandatory
     * columns + {@link #COLLAPSED_COLUMNS} + Remarks' 3 columns. Deliberately varied: a fully-detailed
     * bank-repo vehicle, a lighter insurance-salvage item, and a plain electronics item using none of
     * the detail columns at all — showing the range from "fill in everything" to "ignore the detail
     * columns entirely." Both vehicle-category rows still carry Registration Number/Chassis No/Yard
     * Name/Yard Location since requireVehicleDetails makes those 4 mandatory for any non-NEW item in
     * a vehicle category (Vehicles/Bank Vehicles/Auto/Insurance), and a Vehicle Type — a single row is
     * always one vehicle type, never a mix (see {@link #VEHICLE_TYPE_ATTR_KEY}); Electronics leaves it
     * blank since it isn't a vehicle category.
     */
    private static final String[][] EXAMPLE_ROWS = {
            // Title, Category, Brand, Condition, City, State, Zip, Base Price, Start, End, Swipe Stock, Vehicle Type,
            // Registration Number, Chassis No, Yard Name, Yard Location,
            // General Details (list), Registration (list), Insurance (list), Other Details (list),
            // Repo Date, Parking Rate (per day), Additional Remarks
            {"Mahindra Bolero Pickup (Repo)", "Bank Vehicles", "Mahindra", "USED", "Madanapalle", "Andhra Pradesh",
                    "517325", "130200", "2026-08-01 10:00", "2026-08-05 18:00", "FALSE", "CV",
                    "AP02Y8911", "MD2B77AX3PWA26654", "Shriram Yard Bengaluru", "Shriram Yard Bengaluru, Survey No 52/1A",
                    "Power Steering: No\nPayment Terms: Payment to be made within 24 hours from the time of approval\n"
                            + "RC Book Available: No\nSeller Reference: L2ATHI10845213\nSun Roof: No\n"
                            + "CTE Contact Person: Tulsi B\nCTE Contact Person Phone: 9892803643",
                    "Year of Manufacturing: 2015",
                    "",
                    "Hypothecation: No\nHas Loan Been Paid Off: No\nWhether Valid Form 35 NOC Available: No\n"
                            + "Listing Remarks: L2A_THI10845213\nFaremeter: No\nEngine No: PFXWPA18287",
                    "2026-07-17", "100", "Bids once placed cannot be cancelled. Parking charges to be paid by buyer as per seller terms."},
            {"Water-Damaged Hyundai Creta (Salvage)", "Insurance", "Hyundai", "FOR_PARTS", "Chennai", "Tamil Nadu",
                    "600001", "260000", "2026-08-02 09:00", "2026-08-06 18:00", "FALSE", "4W",
                    "TN09CD5678", "MA3ETEB1S00123456", "IDBI Yard Chennai", "IDBI Yard Chennai, Guindy Industrial Estate",
                    "",
                    "Year of Manufacturing: 2021",
                    "Insurance Provider: ICICI Lombard\nInsurance Valid Upto: 2027-03-31",
                    "",
                    "", "", "Flood-damaged; sold as-is for parts/scrap only."},
            {"Sealed Dell Laptop (Grade A)", "Electronics", "Dell", "NEW", "Hyderabad", "Telangana",
                    "500001", "45000", "", "", "TRUE", "",
                    "", "", "", "",
                    "", "", "", "",
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
        for (String key : MANDATORY_DETAIL_KEYS) {
            String value = attributes == null ? null : attributes.get(key);
            if (value == null || value.isBlank()) missing.add(detailColumnLabel(key));
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException(String.join(", ", missing)
                    + (missing.size() > 1 ? " are required" : " is required")
                    + " for a used " + category.getName() + " item (only exempt for Condition = NEW)");
        }
    }

    private static String detailColumnLabel(String key) {
        for (String[] col : DETAIL_COLUMNS) {
            if (col[1].equals(key)) return col[0];
        }
        return key;
    }

    /**
     * Parses one collapsed tab's cell text (one "Label: value" pair per line) back into
     * {@code {ListingAttribute key: value}} entries — the bulk-import mirror of
     * {@code frontend/src/detailFields.ts}'s {@code parseDetailListText}. A line whose label doesn't
     * match any of that tab's non-mandatory {@link #DETAIL_COLUMNS} headers (case-insensitive) isn't
     * dropped — its raw trimmed label becomes the attribute key, which the detail page's existing
     * fallback rendering already displays sensibly instead of silently losing it.
     */
    private static Map<String, String> parseDetailListCell(String tab, String cellText) {
        Map<String, String> labelToKey = new java.util.HashMap<>();
        for (String[] col : DETAIL_COLUMNS) {
            if (col[2].equals(tab) && !MANDATORY_DETAIL_KEYS.contains(col[1])) {
                labelToKey.put(col[0].trim().toLowerCase(), col[1]);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        if (cellText == null) return result;
        for (String line : cellText.split("\n")) {
            int idx = line.indexOf(':');
            if (idx < 0) continue;
            String rawLabel = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (rawLabel.isEmpty() || value.isEmpty()) continue;
            String key = labelToKey.getOrDefault(rawLabel.toLowerCase(), rawLabel);
            result.put(key, value);
        }
        return result;
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
