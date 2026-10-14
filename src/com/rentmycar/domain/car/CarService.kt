package com.rentmycar.domain.car

import com.rentmycar.appEnvironment
import com.rentmycar.database.DatabaseSettings
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
        
        val settings = DatabaseSettings(
            url = appEnvironment["DATABASE_URL"] ?: "jdbc:mysql://127.0.0.1:3307/rentmycar",
            user = appEnvironment["MYSQL_USER"] ?: "root",
            password = appEnvironment["MYSQL_PASSWORD"] ?: "root"
        )
        
        logger.info("Connecting to database: ${settings.url}")
        
        val picturesJson = request.pictures?.let { json.encodeToString(it) }
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.autoCommit = false
            
            try {
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
                
                logger.info("Checking license plate uniqueness: ${request.licensePlate}")
                val licensePlateExists = connection.prepareStatement(
                    "SELECT COUNT(*) FROM cars WHERE licensePlate = ?"
                ).use { stmt ->
                    stmt.setString(1, request.licensePlate)
                    stmt.executeQuery().use { rs ->
                        rs.next() && rs.getLong(1) > 0
                    }
                }
                
                if (licensePlateExists) {
                    logger.error("Car with license plate ${request.licensePlate} already exists")
                    throw IllegalArgumentException("Er bestaat al een auto met kenteken ${request.licensePlate}")
                }
                
                logger.info("Inserting car into database")
                val carId = connection.prepareStatement(
                    """INSERT INTO cars (ownerId, brand, type, licensePlate, pricePerDay, pickupId, dropOffId, pictures, isAvailable)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                ).use { stmt ->
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
                    
                    stmt.generatedKeys.use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get generated ID")
                    }
                }
                
                logger.info("Car inserted with ID: $carId")
                
                val car = connection.prepareStatement(
                    "SELECT * FROM cars WHERE id = ?"
                ).use { stmt ->
                    stmt.setLong(1, carId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs else throw IllegalStateException("Failed to fetch created car")
                    }
                }
                
                connection.commit()
                logger.info("Transaction committed successfully")
                
                val picturesList = car.getString("pictures")?.let { 
                    if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                }
                
                return CarResponse(
                    id = car.getLong("id"),
                    ownerId = car.getLong("ownerId"),
                    brand = car.getString("brand"),
                    type = car.getString("type"),
                    licensePlate = car.getString("licensePlate"),
                    pricePerDay = car.getBigDecimal("pricePerDay").toDouble(),
                    pickupLocationId = car.getLong("pickupId"),
                    dropOffLocationId = car.getLong("dropOffId"),
                    pictures = picturesList,
                    isAvailable = car.getBoolean("isAvailable")
                )
            } catch (e: Exception) {
                logger.error("Error adding car: ${e.message}", e)
                connection.rollback()
                throw e
            }
        }
    }
}
