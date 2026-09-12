
package com.meteorfix.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.meteorfix.gui.mixin.WWindowTitleAccessor;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindowPositionMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger("meteor-gui-position-fix");
    private static final AtomicBoolean LOGGED_FIRST_LAYOUT = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_FIRST_MOVE = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_FIRST_RESOLVE = new AtomicBoolean(false);

    
    private static final ThreadLocal<Boolean> CLAMPING = ThreadLocal.withInitial(() -> false);

    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("meteor-gui-position-fix.json");

    private static final Map<String, double[]> POSITIONS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    
    private static final Set<WWindow> ACTIVE_WINDOWS =
        Collections.newSetFromMap(new WeakHashMap<>());

    
    private static final double GAP = 4.0;

    private static Field EXPANDED_FIELD;
    private static Field ANIM_PROGRESS_FIELD;
    private static Field HEADER_FIELD;
    private static Field DRAGGING_FIELD;
    private static boolean reflectionReady = false;

    static {
        try {
            EXPANDED_FIELD = WWindow.class.getDeclaredField("expanded");
            EXPANDED_FIELD.setAccessible(true);
            ANIM_PROGRESS_FIELD = WWindow.class.getDeclaredField("animProgress");
            ANIM_PROGRESS_FIELD.setAccessible(true);
            HEADER_FIELD = WWindow.class.getDeclaredField("header");
            HEADER_FIELD.setAccessible(true);
            DRAGGING_FIELD = WWindow.class.getDeclaredField("dragging");
            DRAGGING_FIELD.setAccessible(true);
            reflectionReady = true;
        } catch (Throwable t) {
            LOGGER.warn("[MeteorGuiPositionFix] Could not prepare reflection for WWindow state.", t);
        }
    }

    private WindowPositionMemory() {
    }

    public static boolean isClamping() {
        return Boolean.TRUE.equals(CLAMPING.get());
    }

    
    public static void afterLayout(WWindow window) {
        ensureLoaded();
        ACTIVE_WINDOWS.add(window);

        if (LOGGED_FIRST_LAYOUT.compareAndSet(false, true)) {
            LOGGER.info("[MeteorGuiPositionFix] Window layout hook is active.");
        }

        String key = keyFor(window);
        if (key != null) {
            double[] saved = POSITIONS.get(key);
            if (saved != null) {
                
                
                double dx = saved[0] - window.x;
                double dy = saved[1] - window.y;
                if (dx != 0 || dy != 0) {
                    CLAMPING.set(true);
                    try {
                        window.move(dx, dy);
                    } finally {
                        CLAMPING.set(false);
                    }
                }
            } else {
                POSITIONS.put(key, new double[]{window.x, window.y});
                save();
            }
        }

        clampToScreen(window);
    }

    
    public static void afterMove(WWindow window) {
        if (isClamping()) return;

        if (LOGGED_FIRST_MOVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorGuiPositionFix] Window move hook is active.");
        }

        clampToScreen(window);

        String key = keyFor(window);
        if (key == null) return;

        
        
        POSITIONS.put(key, new double[]{window.x, window.y});
    }

    
    public static void afterDragEnd(WWindow window) {
        if (isClamping()) return;

        if (LOGGED_FIRST_RESOLVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorGuiPositionFix] Overlap-resolve-on-release is active.");
        }

        resolveOverlaps(window);
        clampToScreen(window);
        ((com.meteorfix.gui.mixin.WWindowDragStateAccessor) (Object) window).meteorGuiPositionFix$setMoved(false);
        ((com.meteorfix.gui.mixin.WWindowDragStateAccessor) (Object) window).meteorGuiPositionFix$setMovedX(window.x);
        ((com.meteorfix.gui.mixin.WWindowDragStateAccessor) (Object) window).meteorGuiPositionFix$setMovedY(window.y);

        String key = keyFor(window);
        if (key == null) return;

        POSITIONS.put(key, new double[]{window.x, window.y});
        save();
    }

    
    public static void clampToScreen(WWindow window) {
        
        
        
        final double topBar = 40.0;

        double maxX = Utils.getWindowWidth() - window.width;
        double maxY = Utils.getWindowHeight() - effectiveHeight(window);

        double newX = window.x;
        double newY = window.y;

        if (newX > maxX) newX = Math.max(0, maxX);
        if (newY > maxY) newY = Math.max(topBar, maxY);
        if (newX < 0) newX = 0;
        if (newY < topBar) newY = topBar;

        double dx = newX - window.x;
        double dy = newY - window.y;

        if (dx == 0 && dy == 0) return;

        
        
        CLAMPING.set(true);
        try {
            window.move(dx, dy);
        } finally {
            CLAMPING.set(false);
        }
    }

    
    public static void resolveOverlaps(WWindow window) {
        List<WWindow> others = collectSiblings(window);
        if (others.isEmpty()) return;

        final double originX = window.x;
        final double originY = window.y;
        final double ww = window.width;
        final double wh = effectiveHeight(window);

        
        if (isFree(originX, originY, ww, wh, others)) return;

        final double topBar = 40.0;
        final double screenW = Utils.getWindowWidth();
        final double screenH = Utils.getWindowHeight();
        final double minX = 0;
        final double minY = topBar;
        final double maxX = Math.max(minX, screenW - ww);
        final double maxY = Math.max(minY, screenH - wh);

        double bestX = originX;
        double bestY = originY;
        double bestDist = Double.POSITIVE_INFINITY;
        boolean found = false;

        
        for (WWindow other : others) {
            double ox = other.x;
            double oy = other.y;
            double ow = other.width;
            double oh = effectiveHeight(other);

            double[][] sideCandidates = {
                
                { ox + ow + GAP, originY },
                
                { ox - ww - GAP, originY },
                
                { originX, oy + oh + GAP },
                
                { originX, oy - wh - GAP },
                
                { ox + ow + GAP, oy },
                { ox - ww - GAP, oy },
                { ox, oy + oh + GAP },
                { ox, oy - wh - GAP },
                { ox + ow + GAP, oy + oh - wh },
                { ox - ww - GAP, oy + oh - wh },
                { ox + ow - ww, oy + oh + GAP },
                { ox + ow - ww, oy - wh - GAP },
            };

            for (double[] c : sideCandidates) {
                double cx = clamp(c[0], minX, maxX);
                double cy = clamp(c[1], minY, maxY);
                if (!isFree(cx, cy, ww, wh, others)) continue;
                double d = dist2(cx, cy, originX, originY);
                if (d < bestDist) {
                    bestDist = d;
                    bestX = cx;
                    bestY = cy;
                    found = true;
                }
            }
        }

        
        
        
        final double step = 8.0;
        final int maxRings = 80; 

        for (int ring = 1; ring <= maxRings; ring++) {
            double radius = ring * step;
            
            int samples = Math.max(8, (int) (2 * Math.PI * radius / step));
            for (int s = 0; s < samples; s++) {
                double angle = (2 * Math.PI * s) / samples;
                double cx = clamp(originX + Math.cos(angle) * radius, minX, maxX);
                double cy = clamp(originY + Math.sin(angle) * radius, minY, maxY);
                if (!isFree(cx, cy, ww, wh, others)) continue;
                double d = dist2(cx, cy, originX, originY);
                if (d < bestDist) {
                    bestDist = d;
                    bestX = cx;
                    bestY = cy;
                    found = true;
                }
            }
            
            
            if (found && bestDist <= radius * radius) break;
        }

        if (!found) {
            
            
            return;
        }

        double dx = bestX - window.x;
        double dy = bestY - window.y;
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return;

        CLAMPING.set(true);
        try {
            window.move(dx, dy);
        } finally {
            CLAMPING.set(false);
        }
    }

    private static List<WWindow> collectSiblings(WWindow window) {
        List<WWindow> others = new ArrayList<>();
        for (WWindow other : ACTIVE_WINDOWS) {
            if (other == null || other == window) continue;
            if (other.parent == null) continue;
            
            if (window.parent != null && other.parent != window.parent) continue;
            others.add(other);
        }
        return others;
    }

    
    private static boolean isFree(double x, double y, double w, double h, List<WWindow> others) {
        for (WWindow other : others) {
            double ox = other.x;
            double oy = other.y;
            double ow = other.width;
            double oh = effectiveHeight(other);

            
            if (x < ox + ow + GAP
                && x + w > ox - GAP
                && y < oy + oh + GAP
                && y + h > oy - GAP) {
                return false;
            }
        }
        return true;
    }

    private static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    
    public static double effectiveHeight(WWindow window) {
        if (!reflectionReady) {
            return Math.min(28.0, window.height > 0 ? window.height : 28.0);
        }

        try {
            boolean expanded = EXPANDED_FIELD.getBoolean(window);
            double animProgress = ANIM_PROGRESS_FIELD.getDouble(window);
            Object rawHeader = HEADER_FIELD.get(window);
            WWidget header = rawHeader instanceof WWidget ? (WWidget) rawHeader : null;

            if (expanded && animProgress >= 0.999) {
                return window.height;
            }

            double headerHeight;
            if (header != null && header.height > 0) {
                headerHeight = header.height;
            } else {
                headerHeight = Math.min(28.0, window.height > 0 ? window.height : 28.0);
            }

            return (window.height - headerHeight) * animProgress + headerHeight;
        } catch (Throwable t) {
            return Math.min(28.0, window.height > 0 ? window.height : 28.0);
        }
    }

    private static String keyFor(WWindow window) {
        String id = window.id;
        if (id != null && !id.isBlank()) {
            return "id:" + id;
        }

        String title = safeTitle(window);
        if (title != null && !title.isBlank()) {
            return "title:" + title;
        }

        return null;
    }

    private static String safeTitle(WWindow window) {
        try {
            return ((WWindowTitleAccessor) (Object) window).meteorGuiPositionFix$getTitle();
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                Type type = new TypeToken<Map<String, double[]>>() {}.getType();
                Map<String, double[]> onDisk = GSON.fromJson(json, type);
                if (onDisk != null) {
                    POSITIONS.putAll(onDisk);
                    LOGGER.info("[MeteorGuiPositionFix] Loaded {} saved window position(s).", onDisk.size());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[MeteorGuiPositionFix] Could not read saved positions, starting fresh.", e);
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(POSITIONS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[MeteorGuiPositionFix] Could not save window positions.", e);
        }
    }
}
