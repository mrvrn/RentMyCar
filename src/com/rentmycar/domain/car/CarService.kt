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
                
                connection.commit()
                logger.info("Transaction committed successfully")
                
                val picturesList = (carData["pictures"] as? String)?.let { 
                    if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                }
                
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
                logger.error("Error adding car: ${e.message}", e)
                connection.rollback()
                throw e
            }
        }
    }
    
    fun getAllCars(): List<CarResponse> {
        logger.info("Fetching all cars")
        
        val settings = DatabaseSettings(
            url = appEnvironment["DATABASE_URL"] ?: "jdbc:mysql://127.0.0.1:3307/rentmycar",
            user = appEnvironment["MYSQL_USER"] ?: "root",
            password = appEnvironment["MYSQL_PASSWORD"] ?: "root"
        )
        
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            val cars = connection.prepareStatement(
                "SELECT * FROM cars"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<CarResponse>()
                    while (rs.next()) {
                        val picturesJson = rs.getString("pictures")
                        val picturesList = picturesJson?.let { 
                            if (it.isNotBlank()) json.decodeFromString<List<String>>(it) else null
                        }
                        
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
}
