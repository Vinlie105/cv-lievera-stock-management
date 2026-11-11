package com.cvlievera.stockmanagement.inventory;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class StockTransactionController {

    private final StockTransactionService transactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockTransactionDTO.Response>>> getAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockTransaction.TransactionType type,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        Page<StockTransactionDTO.Response> result;
        if (productId != null) {
            result = transactionService.getTransactionsByProduct(productId, pageable);
        } else if (type != null) {
            result = transactionService.getTransactionsByType(type, pageable);
        } else {
            result = transactionService.getAllTransactions(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransactionDTO.Response>> create(
            @Valid @RequestBody StockTransactionDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(transactionService.createTransaction(request)));
    }
}
