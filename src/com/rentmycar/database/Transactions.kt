package com.rentmycar.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction

/**
 * The withTransaction() function runs a suspending block within a new top-level database transaction.
 * The coroutine context is switched to Dispatchers.IO so that blocking JDBC operations run on a thread pool optimized for IO tasks.
 */
suspend fun <T> withTransaction(block: suspend JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
    inTopLevelSuspendTransaction { block() }
}