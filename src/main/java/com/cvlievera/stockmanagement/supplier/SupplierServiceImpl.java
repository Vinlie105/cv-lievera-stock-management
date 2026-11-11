package com.cvlievera.stockmanagement.supplier;

import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    public Page<SupplierDTO.Response> getAllSuppliers(Pageable pageable) {
        return supplierRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public SupplierDTO.Response getSupplierById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public Page<SupplierDTO.Response> searchSuppliers(String name, Pageable pageable) {
        return supplierRepository.findByNameContainingIgnoreCase(name, pageable).map(this::toResponse);
    }

    @Override
    public SupplierDTO.Response createSupplier(SupplierDTO.Request request) {
        if (request.getEmail() != null && supplierRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Supplier with email '" + request.getEmail() + "' already exists");
        }
        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .contactPerson(request.getContactPerson())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();
        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    public SupplierDTO.Response updateSupplier(Long id, SupplierDTO.Request request) {
        Supplier supplier = findById(id);
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    public void deleteSupplier(Long id) {
        supplierRepository.delete(findById(id));
    }

    private Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private SupplierDTO.Response toResponse(Supplier supplier) {
        return SupplierDTO.Response.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .contactPerson(supplier.getContactPerson())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .address(supplier.getAddress())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}
