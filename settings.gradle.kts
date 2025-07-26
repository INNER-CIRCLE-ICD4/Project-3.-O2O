rootProject.name = "ddak-ta"

include(
    "services:gateway",
    "services:auth-service",
    "services:matching-service",
    "services:location-service",
    "services:payment-service",
    "services:notification-service",
    "common:domain",
    "common:events",
    "common:utils",
    "common:user-client"
)