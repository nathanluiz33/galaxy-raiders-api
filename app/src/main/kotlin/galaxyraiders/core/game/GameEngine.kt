package galaxyraiders.core.game

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import galaxyraiders.Config
import galaxyraiders.ports.RandomGenerator
import galaxyraiders.ports.ui.Controller
import galaxyraiders.ports.ui.Controller.PlayerCommand
import galaxyraiders.ports.ui.Visualizer
import org.json.JSONArray
import java.io.File
import java.io.FileWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

const val MILLISECONDS_PER_SECOND: Int = 1000
const val INF: Double = 10000000000.0

object GameEngineConfig {
  private val config = Config(prefix = "GR__CORE__GAME__GAME_ENGINE__")

  val frameRate = config.get<Int>("FRAME_RATE")
  val spaceFieldWidth = config.get<Int>("SPACEFIELD_WIDTH")
  val spaceFieldHeight = config.get<Int>("SPACEFIELD_HEIGHT")
  val asteroidProbability = config.get<Double>("ASTEROID_PROBABILITY")
  val coefficientRestitution = config.get<Double>("COEFFICIENT_RESTITUTION")

  val msPerFrame: Int = MILLISECONDS_PER_SECOND / this.frameRate
}

data class GameState(
  @JsonProperty("score")
  var score: Double,
  @JsonProperty("startTime")
  var startTime: String,
  @JsonProperty("endTime")
  var endTime: String
)

@Suppress("TooManyFunctions")
class GameEngine(
  val generator: RandomGenerator,
  val controller: Controller,
  val visualizer: Visualizer,
) {
  val field = SpaceField(
    width = GameEngineConfig.spaceFieldWidth,
    height = GameEngineConfig.spaceFieldHeight,
    generator = generator
  )

  var playing = true

  var state = GameState(score = 0.0, startTime = "", endTime = "")

  fun getCurrentTimeAsString(): String {
    val currentTime = LocalTime.now()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return currentTime.format(formatter)
  }

  fun saveScore() {
    if (state.startTime == "") state.startTime = getCurrentTimeAsString()
    state.endTime = getCurrentTimeAsString()

    val scoreboard = File("/home/gradle/galaxy-raiders/app/src/main/kotlin/galaxyraiders/core/score/Scoreboard.json")
    val scoreboardWriter = FileWriter(scoreboard)
    val objectMapper = ObjectMapper()
    val json = objectMapper.writeValueAsString(this.state)
    scoreboardWriter.write(json)
    scoreboardWriter.close()
  }

  fun saveLeaderboard() {
    val leaderboardPath = "/home/gradle/galaxy-raiders/app/src/main/kotlin/galaxyraiders/core/score/Leaderboard.json"
    // val leaderboardJson = getJsonObjectFromFile(leaderboardPath)

    val file = File(leaderboardPath)
    val jsonContent = file.readText()
    val jsonArray = JSONArray(jsonContent)

    var mnScore: Double = INF
    for (i in 0 until jsonArray.length()) {
      val jsonObject = jsonArray.get(i)

      if (jsonObject.get("startTime") == state.startTime.toString()) {
        val objectMapper = ObjectMapper()
        jsonArray.put(i, objectMapper.writeValueAsString(this.state))
        file.writeText(jsonArray.toString())
        return
      }
      if (mnScore > jsonObject.get("score")) {
        mnScore = jsonObject.get("score")
      }
    }
    if (mnScore < this.state.score) {
      for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.get(i)

        if (mnScore == jsonObject.get("score")) {
          val objectMapper = ObjectMapper()
          jsonArray.put(i, objectMapper.writeValueAsString(this.state))
          file.writeText(jsonArray.toString())
          return
        }
      }
    }
  }

  fun execute() {
    while (true) {
      val duration = measureTimeMillis { this.tick() }

      Thread.sleep(
        maxOf(0, GameEngineConfig.msPerFrame - duration)
      )
    }
  }

  fun execute(maxIterations: Int) {
    repeat(maxIterations) {
      this.tick()
    }
  }

  fun tick() {
    this.processPlayerInput()
    this.updateSpaceObjects()
    this.renderSpaceField()
    this.saveScore()
    this.saveLeaderboard()
  }

  fun processPlayerInput() {
    this.controller.nextPlayerCommand()?.also {
      when (it) {
        PlayerCommand.MOVE_SHIP_UP ->
          this.field.ship.boostUp()
        PlayerCommand.MOVE_SHIP_DOWN ->
          this.field.ship.boostDown()
        PlayerCommand.MOVE_SHIP_LEFT ->
          this.field.ship.boostLeft()
        PlayerCommand.MOVE_SHIP_RIGHT ->
          this.field.ship.boostRight()
        PlayerCommand.LAUNCH_MISSILE ->
          this.field.generateMissile()
        PlayerCommand.PAUSE_GAME ->
          this.playing = !this.playing
      }
    }
  }

  fun updateSpaceObjects() {
    if (!this.playing) return
    this.handleExplosions()
    this.handleCollisions()
    this.handleMissileAsteroidCollisions()
    this.moveSpaceObjects()
    this.trimSpaceObjects()
    this.generateAsteroids()
  }

  fun handleExplosions() {
    this.field.handleExplosions()
  }

  fun handleCollisions() {
    this.field.spaceObjects.forEachPair {
        (first, second) ->
      if (first.impacts(second)) {
        first.collideWith(second, GameEngineConfig.coefficientRestitution)
      }
    }
  }

  fun handleMissileAsteroidCollisions() {
    this.state.score += this.field.handleMissileAsteroidCollisions()
  }

  fun moveSpaceObjects() {
    this.field.moveShip()
    this.field.moveAsteroids()
    this.field.moveMissiles()
  }

  fun trimSpaceObjects() {
    this.field.trimAsteroids()
    this.field.trimMissiles()
  }

  fun generateAsteroids() {
    val probability = generator.generateProbability()

    if (probability <= GameEngineConfig.asteroidProbability) {
      this.field.generateAsteroid()
    }
  }

  fun renderSpaceField() {
    this.visualizer.renderSpaceField(this.field)
  }
}

fun <T> List<T>.forEachPair(action: (Pair<T, T>) -> Unit) {
  for (i in 0 until this.size) {
    for (j in i + 1 until this.size) {
      action(Pair(this[i], this[j]))
    }
  }
}
