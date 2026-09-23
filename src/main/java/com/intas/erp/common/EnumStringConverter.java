package com.intas.erp.common;

import jakarta.persistence.AttributeConverter;

/**
 * Stores an enum as its plain name in a VARCHAR column.
 *
 * <p>Why not {@code @Enumerated(STRING)}: on H2, Hibernate 7 maps that to H2's native
 * {@code ENUM('A','B')} column type, which freezes the allowed values at table
 * creation — adding a new constant later (e.g. a new movement type) makes inserts
 * fail, and {@code ddl-auto=update} never widens the column. A converter keeps the
 * column a plain string, so new enum constants just work.
 */
public abstract class EnumStringConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

  private final Class<E> type;

  protected EnumStringConverter(Class<E> type) {
    this.type = type;
  }

  @Override
  public String convertToDatabaseColumn(E value) {
    return value == null ? null : value.name();
  }

  @Override
  public E convertToEntityAttribute(String dbValue) {
    return dbValue == null ? null : Enum.valueOf(type, dbValue);
  }
}
