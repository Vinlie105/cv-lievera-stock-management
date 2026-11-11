package com.cvlievera.stockmanagement.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    Page<CategoryDTO.Response> getAllCategories(Pageable pageable);

    CategoryDTO.Response getCategoryById(Long id);

    CategoryDTO.Response createCategory(CategoryDTO.Request request);

    CategoryDTO.Response updateCategory(Long id, CategoryDTO.Request request);

    void deleteCategory(Long id);
}
