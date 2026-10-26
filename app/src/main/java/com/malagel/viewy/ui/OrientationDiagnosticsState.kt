package com.malagel.viewy.ui

import com.malagel.viewy.orientation.AcquisitionState
import com.malagel.viewy.orientation.ObservationComparison
import com.malagel.viewy.orientation.OrientationObservation

data class OrientationDiagnosticsState(
    val acquisition: AcquisitionState = AcquisitionState(),
    val first: OrientationObservation? = null,
    val second: OrientationObservation? = null,
    val comparison: ObservationComparison? = null,
    val captureMessage: String = "No captures",
)
