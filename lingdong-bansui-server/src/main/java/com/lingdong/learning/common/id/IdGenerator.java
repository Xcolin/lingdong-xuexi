package com.lingdong.learning.common.id;

/**
 * Allocates globally unique numeric identifiers before persistence writes occur.
 */
public interface IdGenerator {
    long nextId();
}
