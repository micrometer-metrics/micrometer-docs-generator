package io.micrometer.docs.spans.conventions;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;

public interface ObservationConventionInterface extends ObservationConvention<Context> {

    @Override
    default String getName() {
        return "foo-iface";
    }

    @Override
    default boolean supportsContext(Observation.Context context) {
        return true;
    }

}
