package com.reiko.signaltower.client;

/**
 * The geometry of the reach preview ring (v0.37.0), kept free of client classes so the GameTests
 * can check it. A sender at block (x, z) reaches every block whose horizontal offset (dx, dz)
 * from it has dx² + dz² ≤ reach² (see {@code SignalNetwork.Coverage#reaches}), so the ring is
 * drawn half a block outside that, around the middle of the sender's block.
 */
public final class ReachRingShape {
    /** Blocks between dots along the ring. */
    public static final double SPACING = 1.5;
    /** The most dots drawn (a ring of about 9,500 blocks); bigger rings get them farther apart. */
    public static final int MAX_DOTS = 40_000;

    private ReachRingShape() {
    }

    /** The ring's radius, from the middle of the sender's block. */
    public static double radius(int reach) {
        return reach + 0.5;
    }

    /** How many dots a ring for {@code reach} has (at least 16). */
    public static int dotCount(int reach) {
        double circumference = 2 * Math.PI * radius(Math.max(0, reach));
        return (int) Math.max(16, Math.min(MAX_DOTS, Math.ceil(circumference / SPACING)));
    }

    /** Where dot {@code i} of {@code count} is, as {x, z} offsets from the middle of the sender's block (dot 0 is due east). */
    public static double[] dot(int reach, int i, int count) {
        double angle = 2 * Math.PI * i / count;
        double r = radius(reach);
        return new double[]{r * Math.cos(angle), r * Math.sin(angle)};
    }

    // ---- v0.48.0: a Dish Antenna's keyhole ----

    /** The angle (0 due east, turning toward south) a dish aimed {@code aim} points. */
    public static double beamAngle(int aim) {
        return Math.toRadians(aim * 22.5 - 90.0);
    }

    /**
     * The dots of a keyhole ring: the circle at {@code reach} except across the beam, the beam's
     * far edge at {@code far}, and its two sides joining them. Each is {x, z, along x, along z}:
     * where it is (from the middle of the sender's block) and the way the ring runs there.
     */
    public static java.util.List<double[]> keyhole(int reach, int far, int aim, int cone) {
        java.util.List<double[]> dots = new java.util.ArrayList<>();
        double center = beamAngle(aim);
        double half = Math.toRadians(cone);
        double near = radius(reach);
        double out = radius(Math.max(reach, far));
        int around = dotCount(reach);
        for (int i = 0; i < around; i++) {
            double angle = 2 * Math.PI * i / around;
            if (Math.abs(Math.IEEEremainder(angle - center, 2 * Math.PI)) > half) {
                dots.add(new double[]{near * Math.cos(angle), near * Math.sin(angle), -Math.sin(angle), Math.cos(angle)});
            }
        }
        int arc = (int) Math.max(2, Math.min(MAX_DOTS, Math.ceil(2 * half * out / SPACING)));
        for (int i = 0; i <= arc; i++) {
            double angle = center - half + 2 * half * i / arc;
            dots.add(new double[]{out * Math.cos(angle), out * Math.sin(angle), -Math.sin(angle), Math.cos(angle)});
        }
        int side = (int) Math.ceil((out - near) / SPACING);
        for (double edge : new double[]{center - half, center + half}) {
            for (int i = 0; i < side; i++) {
                double r = near + (out - near) * i / side;
                dots.add(new double[]{r * Math.cos(edge), r * Math.sin(edge), Math.cos(edge), Math.sin(edge)});
            }
        }
        return dots;
    }

    /** {@link #edgeDistance(long, long, int)} for a dish: {@code far} inside its beam, {@code reach} elsewhere. */
    public static int edgeDistance(long dx, long dz, int reach, int far, int aim, int cone) {
        return edgeDistance(dx, dz, com.reiko.signaltower.radio.AntennaTier.inBeam(dx, dz, aim, cone) ? Math.max(reach, far) : reach);
    }

    /** True if a block (dx, dz) from the sender is inside its reach: the same rule the network uses. */
    public static boolean inside(long dx, long dz, int reach) {
        return dx * dx + dz * dz <= (long) reach * reach;
    }

    /**
     * How far a block (dx, dz) from the sender is from the edge: 0 or more = inside, with that many
     * blocks to go to the edge; less than 0 = outside by that many blocks.
     */
    public static int edgeDistance(long dx, long dz, int reach) {
        double distance = Math.sqrt((double) (dx * dx + dz * dz));
        if (inside(dx, dz, reach)) {
            return Math.max(0, reach - (int) Math.floor(distance));
        }
        return -Math.max(1, (int) Math.ceil(distance) - reach);
    }
}
