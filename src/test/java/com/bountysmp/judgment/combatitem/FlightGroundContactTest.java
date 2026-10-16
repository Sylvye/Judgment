package com.bountysmp.judgment.combatitem;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class FlightGroundContactTest {
    @Test void solidSupportAndSlabsCountButAirAndSideContactDoNot() {
        assertTrue(grounded(64, 1, true, false, false));
        assertTrue(grounded(63.5, 0.5, true, false, false));
        assertFalse(grounded(64.2, 1, true, false, false));
        assertFalse(grounded(63.5, 1, true, false, false));
    }

    @Test void groundFlagAloneWaterAndGlidingDoNotCountAsLanding() {
        assertFalse(grounded(64, 0, true, false, false));
        assertFalse(grounded(64, 1, false, false, false));
        assertFalse(grounded(64, 1, true, true, false));
        assertFalse(grounded(64, 1, true, false, true));
    }

    private boolean grounded(double feetY, double supportHeight, boolean onGround, boolean water, boolean gliding) {
        // Supply collision shapes directly because MockBukkit has no implementation for them.
        World world = stub(World.class, (method, args) -> {
            if (!method.equals("getBlockAt")) throw new AssertionError(method);
            boolean support = (int) args[0] == 0 && (int) args[1] == 63 && (int) args[2] == 0;
            return stub(Block.class, (blockMethod, blockArgs) -> {
                if (!blockMethod.equals("getCollisionShape")) throw new AssertionError(blockMethod);
                return stub(VoxelShape.class, (shapeMethod, shapeArgs) -> {
                    if (!shapeMethod.equals("getBoundingBoxes")) throw new AssertionError(shapeMethod);
                    return support && supportHeight > 0 ? List.of(new BoundingBox(0, 0, 0, 1, supportHeight, 1)) : List.of();
                });
            });
        });
        Player player = stub(Player.class, (method, args) -> switch (method) {
            case "isOnGround" -> onGround;
            case "isInWater" -> water;
            case "isGliding" -> gliding;
            case "getVelocity" -> new Vector();
            case "getBoundingBox" -> new BoundingBox(0.2, feetY, 0.2, 0.8, feetY + 1.8, 0.8);
            case "getWorld" -> world;
            default -> throw new AssertionError(method);
        });
        return FlightLandingRequirement.confirmedGrounded(player);
    }

    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> calls.apply(method.getName(), args)));
    }
}
