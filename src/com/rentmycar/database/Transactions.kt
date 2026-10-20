package com.rentmycar.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Runs [block] inside a database transaction.
 *
 * Starts a transaction if needed, or uses the existing transaction.
 * Changes are committed when the transaction succeeds and rolled back if it fails.
 *
 * Runs blocking database calls on [Dispatchers.IO] to avoid blocking the calling
 * thread. The block can also call suspending functions.
 *
 * **Hyperskill:**
 * A transaction groups database operations into a single unit of work.
 * Either all changes are committed together, or they are rolled back together.
 */
suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction { block() }
}