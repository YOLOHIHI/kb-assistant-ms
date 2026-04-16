package com.codec.kb.index;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts float[] to pgvector string format "[0.1,0.2,...]" and back.
 * The physical column type is PostgreSQL pgvector's native vector type.
 */
@Converter
public class FloatArrayConverter implements AttributeConverter<float[], String> {
  @Override
  public String convertToDatabaseColumn(float[] arr) {
    if (arr == null || arr.length == 0) return null;
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(arr[i]);
    }
    sb.append(']');
    return sb.toString();
  }

  @Override
  public float[] convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) return new float[0];
    String s = dbData.trim();
    if (s.startsWith("[")) s = s.substring(1);
    if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
    if (s.isBlank()) return new float[0];
    String[] parts = s.split(",");
    float[] arr = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      arr[i] = Float.parseFloat(parts[i].trim());
    }
    return arr;
  }
}
