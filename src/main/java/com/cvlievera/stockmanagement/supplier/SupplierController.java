package com.cvlievera.stockmanagement.supplier;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplierDTO.Response>>> getAll(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(supplierService.searchSuppliers(search, pageable))));
        }
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(supplierService.getAllSuppliers(pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierDTO.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getSupplierById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierDTO.Response>> create(@Valid @RequestBody SupplierDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(supplierService.createSupplier(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierDTO.Response>> update(@PathVariable Long id,
                                                                      @Valid @RequestBody SupplierDTO.Request request) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.updateSupplier(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
