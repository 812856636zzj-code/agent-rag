package com.example.rag.entity;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.math.BigDecimal;

/** Maps application confidence values to Oracle NUMBER columns. */
@Converter
public class ConfidenceConverter implements AttributeConverter<Double, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    @Override
    public Double convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
