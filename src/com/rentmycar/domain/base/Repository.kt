package com.rentmycar.domain.base

import com.rentmycar.database.withTransaction
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * Shared database operations for entities with Long IDs.
 *
 * E is the Exposed DAO type; D is the DTO returned to callers.
 * Subclasses supply their DAO companion object as entities and implement toDto.
 * Create and update stay in each subclass because their fields and rules differ.
 */
abstract class BaseRepository<E : LongEntity, D>(
    private val entities: LongEntityClass<E>
) {
    // Each subclass decides which entity fields belong in its response DTO.
    protected abstract fun toDto(entity: E): D

    /** Returns the mapped DTO, or null when the ID does not exist. */
    open suspend fun findById(id: Long): D? = withTransaction {
        // Read DAO fields while the transaction is open; only the DTO leaves it.
        entities.findById(id)?.let { toDto(it) }
    }

    open suspend fun findAll(): List<D> = withTransaction {
        entities.all().map { toDto(it) }
    }

    /**
     * Returns true after deletion, or false when the ID does not exist.
     * Database failures (such as foreign key restrictions) propagate as exceptions.
     * Both operations are open so subclasses can override them for special rules.
     */
    open suspend fun delete(id: Long): Boolean = withTransaction {
        // This labeled return exits the transaction block with false.
        val entity = entities.findById(id) ?: return@withTransaction false
        entity.delete()
        true
    }
}
