package com.cvlievera.stockmanagement.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupplierService {

    Page<SupplierDTO.Response> getAllSuppliers(Pageable pageable);

    SupplierDTO.Response getSupplierById(Long id);

    Page<SupplierDTO.Response> searchSuppliers(String name, Pageable pageable);

    SupplierDTO.Response createSupplier(SupplierDTO.Request request);

    SupplierDTO.Response updateSupplier(Long id, SupplierDTO.Request request);

    void deleteSupplier(Long id);
}
