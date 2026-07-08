package com.hotelopai.unimock.application.simulation

open class SimulationException(message: String) : RuntimeException(message)

class SimulationNotLoadedException : SimulationException("No simulation is currently loaded")

class InvalidSimulationSeedPathException(path: String) :
    SimulationException("Simulation seed path is invalid or missing: $path")

class InvalidSimulationSeedException(message: String) :
    SimulationException(message)
