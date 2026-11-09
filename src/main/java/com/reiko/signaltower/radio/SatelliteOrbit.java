package com.reiko.signaltower.radio;

import org.joml.Vector3f;

import java.util.Random;

/**
 * Where a satellite is in the sky (v0.61.0), worked out once from its seed: a great circle tipped
 * {@code 10..70} degrees from overhead, round once every 3 to 7 minutes (so it takes half that to
 * cross the sky), and where on it the satellite was at launch. The client draws it there.
 */
public record SatelliteOrbit(long launchedAt, Vector3f along, Vector3f across, double period, double phase) {
    public static SatelliteOrbit of(SignalNetwork.Satellite satellite) {
        Random random = new Random(satellite.seed());
        double azimuth = random.nextDouble() * Math.PI * 2;
        double tilt = Math.toRadians(10 + random.nextDouble() * 60);
        double period = 3600 + random.nextInt(4800);
        double phase = random.nextDouble() * Math.PI * 2;
        Vector3f along = new Vector3f((float) Math.cos(azimuth), 0, (float) Math.sin(azimuth));
        Vector3f side = new Vector3f((float) -Math.sin(azimuth), 0, (float) Math.cos(azimuth));
        Vector3f across = new Vector3f(0, (float) Math.cos(tilt), 0).add(side.mul((float) Math.sin(tilt)));
        return new SatelliteOrbit(satellite.launchedAt(), along, across, period, phase);
    }

    /** Which way it is in the sky (a unit vector; up is +Y) at game time {@code time}. */
    public Vector3f direction(double time) {
        double angle = phase + Math.PI * 2 * (time - launchedAt) / period;
        return new Vector3f(along).mul((float) Math.cos(angle)).add(new Vector3f(across).mul((float) Math.sin(angle)));
    }
}
