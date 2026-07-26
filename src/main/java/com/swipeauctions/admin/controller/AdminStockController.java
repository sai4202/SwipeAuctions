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

    private final CatalogService catalogService;
    private final AuctionService auctionService;
    private final StorageProvider storageProvider;
    private final PlatformAccountService platformAccountService;

    // ---- Single item ----

    @PostMapping("/listings")
    public StockListingResponse createListing(@Valid @RequestBody CreateStockListingRequest req) {
        User seller = platformAccountService.getOrCreateSwipeStockSeller();
        Category category = resolveCategory(req.categoryId(), req.categoryName());
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
     * Brand, Condition, City, State, Zip, Base Price, Start Time, End Time, Swipe Stock. Only Title,
     * Category and Base Price are required — everything else has a sensible default. A row-level
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

                    Category category = catalogService.resolveOrCreateCategory(categoryName);
                    Listing listing = catalogService.createListing(seller, category.getId(), title,
                            title + " — bulk-imported via admin Add Stock.", brand, condition, city, state, zip,
                            price, Map.of(), effectiveSwipeStock);
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

    /** A ready-to-fill .xlsx with the exact header row the bulk importer expects, plus one example row. */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock");
            String[] headers = {"Title", "Category", "Brand", "Condition", "City", "State", "Zip",
                    "Base Price", "Start Time", "End Time", "Swipe Stock"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);

            Row example = sheet.createRow(1);
            String[] exampleValues = {"iPhone 15 Pro (Sealed)", "Electronics", "Apple", "NEW", "Bengaluru",
                    "KA", "560001", "90000", "2026-08-01 10:00", "2026-08-05 18:00", "FALSE"};
            for (int i = 0; i < exampleValues.length; i++) example.createCell(i).setCellValue(exampleValues[i]);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=swipe-stock-template.xlsx")
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- helpers ----

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
