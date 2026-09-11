/*
 * Meteor GUI Position Fix
 *
 * v3.4.1: on mouse-release the *dragged* category is moved to the nearest
 * free on-screen spot that does not overlap any other category (GAP margin).
 * Only that window is ever moved; siblings are never touched. Candidate
 * search uses side-flush positions against every obstacle plus a dense
 * spiral around the drop point so it cannot land on an already-occupied
 * cell. While the mouse button is held the window moves freely through
 * others; only the final release triggers the search.
 *
 * v3.2/v3.3: collapsed windows must be clamped against their *visible*
 * height (header only, or the animating height), never against the
 * fully-expanded content height that WWindow.height always holds.
 *
 * State (expanded / animProgress / header / dragging) is read via
 * reflection because several fields are protected nested types or package
 * private, and Mixin @Accessor cannot always target them with the
 * desired return type.
 *
 * Clamp / resolve are applied as corrective move() calls so children
 * (header, background, modules) stay glued to the window. A ThreadLocal
 * re-entrancy guard prevents the move mixin from re-entering afterMove
 * during those corrections.
 *
 * effectiveHeight() is also used by WWindowClampMixin so Meteor's own
 * restore clamp inside onCalculateWidgetPositions uses the visible height.
 */
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

    /** Prevents afterMove from running while we apply a clamp/resolve correction. */
    private static final ThreadLocal<Boolean> CLAMPING = ThreadLocal.withInitial(() -> false);

    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("meteor-gui-position-fix.json");

    private static final Map<String, double[]> POSITIONS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    /** Live category windows seen during layout (weak so GC can clean up closed GUIs). */
    private static final Set<WWindow> ACTIVE_WINDOWS =
        Collections.newSetFromMap(new WeakHashMap<>());

    /** Small visual gap left between snapped categories (pixels). */
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

    /**
     * Call from the tail of a window's own layout pass. If we've already
     * pinned a position for this window, forces it back there. Otherwise,
     * treats whatever position it just landed on as the new permanent
     * baseline. Either way, the result is clamped to stay on screen using
     * the *visible* height (collapsed vs expanded).
     */
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
                // Restore via move() so children (header, background, modules)
                // stay attached. Direct x/y assignment would unstick them.
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

    /**
     * Call after every move() while the user is dragging.
     * Only clamps to the screen edge and updates the in-memory pin;
     * overlap resolution is deferred until the mouse is released
     * (see afterDragEnd).
     */
    public static void afterMove(WWindow window) {
        if (isClamping()) return;

        if (LOGGED_FIRST_MOVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorGuiPositionFix] Window move hook is active.");
        }

        clampToScreen(window);

        String key = keyFor(window);
        if (key == null) return;

        // Keep the live pin in sync so a crash mid-drag still has a reasonable
        // last position, but do not write disk on every mouse-move frame.
        POSITIONS.put(key, new double[]{window.x, window.y});
    }

    /**
     * Called when the user releases the mouse after dragging a category.
     * Resolves overlaps against sibling categories, re-clamps, then persists.
     */
    public static void afterDragEnd(WWindow window) {
        if (isClamping()) return;

        if (LOGGED_FIRST_RESOLVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorGuiPositionFix] Overlap-resolve-on-release is active.");
        }

        resolveOverlaps(window);
        clampToScreen(window);

        String key = keyFor(window);
        if (key == null) return;

        POSITIONS.put(key, new double[]{window.x, window.y});
        save();
    }

    /**
     * Keeps a window fully within the current game window bounds, and
     * below the top tab bar (Modules / Config / GUI / …) so categories
     * don't sit under the settings strip where clicks would be stolen.
     * Applies the correction as a move() so header, background and all
     * children stay attached to the window instead of unsticking.
     */
    public static void clampToScreen(WWindow window) {
        // Reserve space for Meteor's top tab bar so categories can't
        // park underneath it. 40px matches the row spacing Meteor itself
        // uses in WCategoryController (theme.scale(40) at default scale).
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

        // Apply as a real move so every child (header, background, modules)
        // is shifted by the same amount. Guard against re-entering afterMove.
        CLAMPING.set(true);
        try {
            window.move(dx, dy);
        } finally {
            CLAMPING.set(false);
        }
    }

    /**
     * Find the nearest on-screen position for the released window that does
     * not overlap any other active category (with a small GAP margin).
     *
     * ONLY the released window is ever moved — other categories are never
     * touched. If the drop position is already free, nothing happens.
     * Otherwise we evaluate candidates (flush against each obstacle side,
     * plus a dense spiral around the drop point) and pick the closest free
     * valid spot.
     */
    public static void resolveOverlaps(WWindow window) {
        List<WWindow> others = collectSiblings(window);
        if (others.isEmpty()) return;

        final double originX = window.x;
        final double originY = window.y;
        final double ww = window.width;
        final double wh = effectiveHeight(window);

        // Already free → nothing to do.
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

        // 1) Candidates flush against each obstacle (4 sides), clamped to screen.
        for (WWindow other : others) {
            double ox = other.x;
            double oy = other.y;
            double ow = other.width;
            double oh = effectiveHeight(other);

            double[][] sideCandidates = {
                // right of other
                { ox + ow + GAP, originY },
                // left of other
                { ox - ww - GAP, originY },
                // below other
                { originX, oy + oh + GAP },
                // above other
                { originX, oy - wh - GAP },
                // also try aligned to other corners for tighter packing
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

        // 2) Dense spiral / ring search around the drop point so we still
        //    find a free cell when side-flush candidates are all blocked.
        //    Step size ~ half a typical header height keeps it responsive.
        final double step = 8.0;
        final int maxRings = 80; // covers a large portion of the screen

        for (int ring = 1; ring <= maxRings; ring++) {
            double radius = ring * step;
            // Approximate number of samples on the ring (more as radius grows)
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
            // Early exit: once we have a candidate inside the current ring
            // radius, further rings can only be farther (or equal).
            if (found && bestDist <= radius * radius) break;
        }

        if (!found) {
            // Screen is completely packed for this size — leave at drop point
            // (still clamped by the caller). Better than inventing a random spot.
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
            // Prefer real siblings under the same parent controller.
            if (window.parent != null && other.parent != window.parent) continue;
            others.add(other);
        }
        return others;
    }

    /** True when the rectangle [x,y,w,h] does not intersect any other (with GAP). */
    private static boolean isFree(double x, double y, double w, double h, List<WWindow> others) {
        for (WWindow other : others) {
            double ox = other.x;
            double oy = other.y;
            double ow = other.width;
            double oh = effectiveHeight(other);

            // Expanded obstacle (GAP on every side).
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

    /**
     * Visible height used for clamping and overlap tests.
     *
     * WWindow never overrides onCalculateSize(), so its `height` field is
     * always the FULLY EXPANDED content height (header + every module),
     * even while collapsed. Only rendering knows the real height:
     *   (height - header.height) * animProgress + header.height
     */
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
