package com.rentmycar.domain.car

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.currentDatabaseSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.sql.DriverManager
import org.slf4j.LoggerFactory

object CarService {
    private val logger = LoggerFactory.getLogger(CarService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    fun addCar(request: AddCarRequest, ownerId: Long): CarResponse {
        logger.info("Adding car for ownerId: $ownerId, brand: ${request.brand}, licensePlate: ${request.licensePlate}")
        
        // Gebruik globale database settings van DatabaseFactory
        val settings = currentDatabaseSettings ?: throw IllegalStateException("Database niet geconfigureerd")
        
        logger.info("Connecting to database: ${settings.url}")
        
        // Encode pictures lijst naar JSON voor opslag in database
        val picturesJson = request.pictures?.let { json.encodeToString(it) }
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            // Auto-commit uit voor transactionele integriteit
            connection.autoCommit = false
            
            try {
                // Valideer dat ophaallocatie bestaat in database
                logger.info("Checking pickup location ID: ${request.pickupLocationId}")
                val pickupExists = connection.prepareStatement(
                    "SELECT COUNT(*) FROM locations WHERE id = ?"
                ).use { stmt ->
                    stmt.setLong(1, request.pickupLocationId)
                    stmt.executeQuery().use { rs ->
                        rs.next() && rs.getLong(1) > 0
                    }
                }
                
                if (!pickupExists) {
                    logger.error("Pickup location with ID ${request.pickupLocationId} does not exist")
                    throw IllegalArgumentException("Ophaallocatie met ID ${request.pickupLocationId} bestaat niet")
                }
                // Ophaallocatie bestaat, ga verder met validatie
                
                // Valideer dat inleverlocatie bestaat in database
                logger.info("Checking dropoff location ID: ${request.dropOffLocationId}")
                val dropoffExists = connection.prepareStatement(
                    "SELECT COUNT(*) FROM locations WHERE id = ?"
                ).use { stmt ->
                    stmt.setLong(1, request.dropOffLocationId)
                    stmt.executeQuery().use { rs ->
                        rs.next() && rs.getLong(1) > 0
                    }
                }
                
                if (!dropoffExists) {
                    logger.error("Dropoff location with ID ${request.dropOffLocationId} does not exist")
                    throw IllegalArgumentException("Inleverlocatie met ID ${request.dropOffLocationId} bestaat niet")
                }
                // Inleverlocatie bestaat, ga verder met invoegen
                
                // Voeg nieuwe auto toe in database
                logger.info("Inserting car into database")
                val carId = connection.prepareStatement(
                    """INSERT INTO cars (ownerId, brand, type, licensePlate, pricePerDay, pickupId, dropOffId, pictures, isAvailable)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                ).use { stmt ->
                    // Bind parameters aan prepared statement
                    stmt.setLong(1, ownerId)
                    stmt.setString(2, request.brand)
                    stmt.setString(3, request.type)
                    stmt.setString(4, request.licensePlate)
                    stmt.setBigDecimal(5, BigDecimal.valueOf(request.pricePerDay))
                    stmt.setLong(6, request.pickupLocationId)
                    stmt.setLong(7, request.dropOffLocationId)
                    stmt.setString(8, picturesJson)
                    stmt.setBoolean(9, request.isAvailable)
                    stmt.executeUpdate()
                    
                    // Haal gegenereerde ID op van de nieuwe auto
                    stmt.generatedKeys.use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get generated ID")
                    }
                }
                
                logger.info("Car inserted with ID: $carId")
                
                // Haal de aangemaakte auto op om complete data terug te geven
                val carData = connection.prepareStatement(
                    "SELECT * FROM cars WHERE id = ?"
                ).use { stmt ->
                    stmt.setLong(1, carId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            mapOf(
                                "id" to rs.getLong("id"),
                                "ownerId" to rs.getLong("ownerId"),
                                "brand" to rs.getString("brand"),
                                "type" to rs.getString("type"),
                                "licensePlate" to rs.getString("licensePlate"),
                                "pricePerDay" to rs.getBigDecimal("pricePerDay").toDouble(),
                                "pickupId" to rs.getLong("pickupId"),
                                "dropOffId" to rs.getLong("dropOffId"),
                                "pictures" to rs.getString("pictures"),
                                "isAvailable" to rs.getBoolean("isAvailable")
                            )
                        } else {
                            throw IllegalStateException("Failed to fetch created car")
                        }
                    }
                }
                
                // Commit transaction om wijzigingen permanent te maken
                connection.commit()
                logger.info("Transaction committed successfully")
                
                // Decodeer JSON terug naar lijst van strings voor pictures
                val picturesList = (carData["pictures"] as? String)?.let { 
                    if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                }
                
                // Map database data naar response DTO
                return CarResponse(
                    id = carData["id"] as Long,
                    ownerId = carData["ownerId"] as Long,
                    brand = carData["brand"] as String,
                    type = carData["type"] as String,
                    licensePlate = carData["licensePlate"] as String,
                    pricePerDay = carData["pricePerDay"] as Double,
                    pickupLocationId = carData["pickupId"] as Long,
                    dropOffLocationId = carData["dropOffId"] as Long,
                    pictures = picturesList,
                    isAvailable = carData["isAvailable"] as Boolean
                )
            } catch (e: Exception) {
                // Rollback transaction bij fout om data consistentie te behouden
                logger.error("Error adding car: ${e.message}", e)
                connection.rollback()
                throw e
            }
        }
    }
    
    fun getAllCars(): List<CarResponse> {
        logger.info("Fetching all cars")
        
        // Gebruik globale database settings van DatabaseFactory
        val settings = currentDatabaseSettings ?: throw IllegalStateException("Database niet geconfigureerd")
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            // Haal alle autos op uit database
            val cars = connection.prepareStatement(
                "SELECT * FROM cars"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<CarResponse>()
                    while (rs.next()) {
                        // Decodeer JSON pictures terug naar lijst
                        val picturesJson = rs.getString("pictures")
                        val picturesList = picturesJson?.let { 
                            if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                        }
                        
                        // Map database rij naar CarResponse DTO
                        result.add(
                            CarResponse(
                                id = rs.getLong("id"),
                                ownerId = rs.getLong("ownerId"),
                                brand = rs.getString("brand"),
                                type = rs.getString("type"),
                                licensePlate = rs.getString("licensePlate"),
                                pricePerDay = rs.getBigDecimal("pricePerDay").toDouble(),
                                pickupLocationId = rs.getLong("pickupId"),
                                dropOffLocationId = rs.getLong("dropOffId"),
                                pictures = picturesList,
                                isAvailable = rs.getBoolean("isAvailable")
                            )
                        )
                    }
                    result
                }
            }
            
            logger.info("Fetched ${cars.size} cars")
            return cars
        }
    }
    
    fun getCarById(id: Long): CarResponse {
        logger.info("Fetching car with ID: $id")
        
        // Gebruik globale database settings van DatabaseFactory
        val settings = currentDatabaseSettings ?: throw IllegalStateException("Database niet geconfigureerd")
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            // Haal specifieke auto op op basis van ID
            val car = connection.prepareStatement(
                "SELECT * FROM cars WHERE id = ?"
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        // Decodeer JSON pictures terug naar lijst
                        val picturesJson = rs.getString("pictures")
                        val picturesList = picturesJson?.let { 
                            if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                        }
                        
                        // Map database rij naar CarResponse DTO
                        CarResponse(
                            id = rs.getLong("id"),
                            ownerId = rs.getLong("ownerId"),
                            brand = rs.getString("brand"),
                            type = rs.getString("type"),
                            licensePlate = rs.getString("licensePlate"),
                            pricePerDay = rs.getBigDecimal("pricePerDay").toDouble(),
                            pickupLocationId = rs.getLong("pickupId"),
                            dropOffLocationId = rs.getLong("dropOffId"),
                            pictures = picturesList,
                            isAvailable = rs.getBoolean("isAvailable")
                        )
                    } else {
                        // Auto niet gevonden, gooi exception
                        throw IllegalArgumentException("Auto met ID $id bestaat niet")
                    }
                }
            }
            
            return car
        }
    }
    
    fun updateCar(id: Long, request: UpdateCarRequest): CarResponse {
        logger.info("Updating car with ID: $id")
        
        // Gebruik globale database settings van DatabaseFactory
        val settings = currentDatabaseSettings ?: throw IllegalStateException("Database niet geconfigureerd")
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            // Auto-commit uit voor transactionele integriteit
            connection.autoCommit = false
            
            try {
                // Haal bestaande auto op om te controleren dat hij bestaat
                val existingCar = getCarById(id)
                
                // Bouw lijst van velden die geupdate moeten worden
                val updates = mutableListOf<String>()
                val values = mutableListOf<Any>()
                
                // Voeg alleen niet-null velden toe aan update lijst (partial update)
                request.brand?.let {
                    updates.add("brand = ?")
                    values.add(it)
                }
                request.type?.let {
                    updates.add("type = ?")
                    values.add(it)
                }
                request.licensePlate?.let {
                    updates.add("licensePlate = ?")
                    values.add(it)
                }
                request.pricePerDay?.let {
                    updates.add("pricePerDay = ?")
                    values.add(BigDecimal.valueOf(it))
                }
                request.pickupLocationId?.let {
                    updates.add("pickupId = ?")
                    values.add(it)
                }
                request.dropOffLocationId?.let {
                    updates.add("dropOffId = ?")
                    values.add(it)
                }
                request.pictures?.let {
                    updates.add("pictures = ?")
                    values.add(json.encodeToString(it))
                }
                request.isAvailable?.let {
                    updates.add("isAvailable = ?")
                    values.add(it)
                }
                
                if (updates.isEmpty()) {
                    logger.info("No fields to update")
                    return existingCar
                }
                // Bouw dynamische SQL update statement met alleen gegeven velden
                values.add(id)
                val sql = "UPDATE cars SET ${updates.joinToString(", ")} WHERE id = ?"
                
                logger.info("Executing update: $sql")
                connection.prepareStatement(sql).use { stmt ->
                    // Bind parameters met correcte types
                    values.forEachIndexed { index, value ->
                        when (value) {
                            is Long -> stmt.setLong(index + 1, value)
                            is String -> stmt.setString(index + 1, value)
                            is Double -> stmt.setDouble(index + 1, value)
                            is BigDecimal -> stmt.setBigDecimal(index + 1, value)
                            is Boolean -> stmt.setBoolean(index + 1, value)
                            else -> stmt.setObject(index + 1, value)
                        }
                    }
                    stmt.executeUpdate()
                }
                
                // Commit transaction om wijzigingen permanent te maken
                connection.commit()
                logger.info("Car updated successfully")
                
                // Haal geupdate auto op en return
                return getCarById(id)
            } catch (e: Exception) {
                // Rollback transaction bij fout om data consistentie te behouden
                logger.error("Error updating car: ${e.message}", e)
                connection.rollback()
                throw e
            }
        }
    }
    
    fun deleteCar(id: Long) {
        logger.info("Deleting car with ID: $id")
        
        // Gebruik globale database settings van DatabaseFactory
        val settings = currentDatabaseSettings ?: throw IllegalStateException("Database niet geconfigureerd")
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            // Verwijder auto uit database
            val rowsAffected = connection.prepareStatement(
                "DELETE FROM cars WHERE id = ?"
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
            
            // Check of auto daadwerkelijk verwijderd is
            if (rowsAffected == 0) {
                throw IllegalArgumentException("Auto met ID $id bestaat niet")
            }
            logger.info("Car deleted successfully")
        }
    }
}
