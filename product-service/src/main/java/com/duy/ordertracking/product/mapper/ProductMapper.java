package com.duy.ordertracking.product.mapper;

import com.duy.ordertracking.product.dto.request.CreateProductRequest;
import com.duy.ordertracking.product.dto.request.UpdateProductRequest;
import com.duy.ordertracking.product.dto.response.ProductResponse;
import com.duy.ordertracking.product.entity.Product;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(CreateProductRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);
}
