package com.cvlievera.stockmanagement.inventory;

import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.exception.ResourceNotFoundException;
import com.cvlievera.stockmanagement.product.Product;
import com.cvlievera.stockmanagement.product.ProductRepository;
import com.cvlievera.stockmanagement.user.User;
import com.cvlievera.stockmanagement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockTransactionServiceImpl implements StockTransactionService {

    private final StockTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public Page<StockTransactionDTO.Response> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public Page<StockTransactionDTO.Response> getTransactionsByProduct(Long productId, Pageable pageable) {
        return transactionRepository.findByProductId(productId, pageable).map(this::toResponse);
    }

    @Override
    public Page<StockTransactionDTO.Response> getTransactionsByType(StockTransaction.TransactionType type, Pageable pageable) {
        return transactionRepository.findByTransactionType(type, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public StockTransactionDTO.Response createTransaction(StockTransactionDTO.Request request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        int newQty = calculateNewQuantity(product.getQuantityInStock(), request);
        product.setQuantityInStock(newQty);
        productRepository.save(product);

        StockTransaction transaction = StockTransaction.builder()
                .product(product)
                .transactionType(request.getTransactionType())
                .quantity(request.getQuantity())
                .notes(request.getNotes())
                .referenceNo(request.getReferenceNo())
                .transactionDate(request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : LocalDateTime.now())
                .performedBy(resolveCurrentUser())
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    private int calculateNewQuantity(int currentQty, StockTransactionDTO.Request request) {
        return switch (request.getTransactionType()) {
            case STOCK_IN -> {
                if (request.getQuantity() <= 0) {
                    throw new BadRequestException("Quantity must be positive for STOCK_IN");
                }
                yield currentQty + request.getQuantity();
            }
            case STOCK_OUT -> {
                if (request.getQuantity() <= 0) {
                    throw new BadRequestException("Quantity must be positive for STOCK_OUT");
                }
                if (request.getQuantity() > currentQty) {
                    throw new BadRequestException(
                            "Insufficient stock. Available: " + currentQty + ", Requested: " + request.getQuantity());
                }
                yield currentQty - request.getQuantity();
            }
            case ADJUSTMENT -> {
                int result = currentQty + request.getQuantity();
                if (result < 0) {
                    throw new BadRequestException(
                            "Adjustment would result in negative stock. Current: " + currentQty + ", Delta: " + request.getQuantity());
                }
                yield result;
            }
        };
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private StockTransactionDTO.Response toResponse(StockTransaction t) {
        return StockTransactionDTO.Response.builder()
                .id(t.getId())
                .productId(t.getProduct().getId())
                .productName(t.getProduct().getName())
                .productSku(t.getProduct().getSku())
                .transactionType(t.getTransactionType())
                .quantity(t.getQuantity())
                .notes(t.getNotes())
                .referenceNo(t.getReferenceNo())
                .transactionDate(t.getTransactionDate())
                .performedByUsername(t.getPerformedBy() != null ? t.getPerformedBy().getUsername() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
