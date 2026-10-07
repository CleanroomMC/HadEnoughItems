package mezz.jei.gui.navigation;

import mezz.jei.util.ErrorUtil;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Calculates a page-navigation strip that stays attached to one horizontal edge while avoiding GUI exclusion
 * areas. Navigation is shortened at its current height before it is moved down, while content keeps the full
 * available width.
 */
public final class NavigationLayout {

    private NavigationLayout() {
    }

    /**
     * Finds the first usable navigation strip within an available area.
     *
     * @param availableArea the full area shared by navigation and content
     * @param exclusionAreas caller-owned GUI areas that navigation must not overlap
     * @param alignment the horizontal edge that navigation must stay attached to
     * @param navigationHeight the fixed navigation height
     * @param minimumNavigationWidth the minimum usable navigation width
     * @param maximumNavigationWidth the preferred maximum navigation width
     * @return the navigation and remaining content areas, or empty when navigation cannot fit
     * @throws NullPointerException if an object argument or exclusion element is null
     * @throws IllegalArgumentException if the available area or a requested dimension is non-positive
     */
    @Nullable
    public static Result calculate(Rectangle availableArea, Collection<Rectangle> exclusionAreas,
                                   Alignment alignment, int navigationHeight,
                                   int minimumNavigationWidth, int maximumNavigationWidth) {
        ErrorUtil.checkNotNull(availableArea, "availableArea");
        ErrorUtil.checkNotNull(exclusionAreas, "exclusionAreas");
        ErrorUtil.checkNotNull(alignment, "alignment");
        validateDimensions(availableArea, navigationHeight, minimumNavigationWidth, maximumNavigationWidth);

        List<Rectangle> exclusions = copyClippedExclusions(availableArea, exclusionAreas);
        int widthLimit = Math.min(availableArea.width, maximumNavigationWidth);
        if (widthLimit < minimumNavigationWidth || availableArea.height < navigationHeight) {
            return null;
        }

        int availableRight = availableArea.x + availableArea.width;
        int availableBottom = availableArea.y + availableArea.height;
        int candidateY = availableArea.y;

        // Each pass either returns a usable anchored interval or advances to the earliest mandatory blocker bottom.
        while (candidateY + navigationHeight <= availableBottom) {
            int stripLeft = alignment == Alignment.LEFT ? availableArea.x : availableRight - widthLimit;
            int stripRight = alignment == Alignment.LEFT ? availableArea.x + widthLimit : availableRight;
            int mandatoryLeft = alignment == Alignment.LEFT ?
                availableArea.x :
                availableRight - minimumNavigationWidth;
            int mandatoryRight = alignment == Alignment.LEFT ?
                availableArea.x + minimumNavigationWidth :
                availableRight;
            int anchoredBoundary = alignment == Alignment.LEFT ? stripRight : stripLeft;
            int earliestBlockingBottom = Integer.MAX_VALUE;
            int candidateBottom = candidateY + navigationHeight;

            for (Rectangle exclusion : exclusions) {
                int exclusionRight = exclusion.x + exclusion.width;
                int exclusionBottom = exclusion.y + exclusion.height;
                if (exclusion.y >= candidateBottom || exclusionBottom <= candidateY) {
                    continue;
                }

                if (exclusion.x < stripRight && exclusionRight > stripLeft) {
                    anchoredBoundary = alignment == Alignment.LEFT ?
                        Math.min(anchoredBoundary, Math.max(stripLeft, exclusion.x)) :
                        Math.max(anchoredBoundary, Math.min(stripRight, exclusionRight));
                }
                if (exclusion.x < mandatoryRight && exclusionRight > mandatoryLeft) {
                    earliestBlockingBottom = Math.min(earliestBlockingBottom, exclusionBottom);
                }
            }

            int navigationWidth = alignment == Alignment.LEFT ?
                anchoredBoundary - stripLeft :
                stripRight - anchoredBoundary;
            if (navigationWidth >= minimumNavigationWidth) {
                int navigationX = alignment == Alignment.LEFT ? availableArea.x : availableRight - navigationWidth;
                Rectangle navigationArea = new Rectangle(
                    navigationX,
                    candidateY,
                    navigationWidth,
                    navigationHeight
                );
                Rectangle contentArea = new Rectangle(
                    availableArea.x,
                    candidateBottom,
                    availableArea.width,
                    availableBottom - candidateBottom
                );
                return new Result(navigationArea, contentArea);
            }

            if (earliestBlockingBottom == Integer.MAX_VALUE) {
                throw new IllegalStateException("A shortened navigation strip has no mandatory-width blocker");
            }
            candidateY = earliestBlockingBottom;
        }
        return null;
    }

    private static void validateDimensions(
        Rectangle availableArea,
        int navigationHeight,
        int minimumNavigationWidth,
        int maximumNavigationWidth
    ) {
        if (availableArea.width <= 0 || availableArea.height <= 0) {
            throw new IllegalArgumentException("availableArea must have positive width and height");
        }
        if (navigationHeight <= 0 || minimumNavigationWidth <= 0 || maximumNavigationWidth <= 0) {
            throw new IllegalArgumentException("navigation dimensions must be positive");
        }
    }

    private static List<Rectangle> copyClippedExclusions(Rectangle availableArea,
                                                         Collection<Rectangle> exclusionAreas) {
        List<Rectangle> clippedExclusions = new ArrayList<>(exclusionAreas.size());
        for (Rectangle exclusionArea : exclusionAreas) {
            Objects.requireNonNull(exclusionArea, "exclusionAreas contains null");
            if (exclusionArea.width <= 0 || exclusionArea.height <= 0) {
                continue;
            }
            Rectangle clipped = availableArea.intersection(exclusionArea);
            if (clipped.width > 0 && clipped.height > 0) {
                clippedExclusions.add(clipped);
            }
        }
        return clippedExclusions;
    }

    /** Selects the horizontal edge that navigation stays attached to. */
    public enum Alignment {
        /** Attach navigation to the left edge. */
        LEFT,
        /** Attach navigation to the right edge. */
        RIGHT
    }

    /**
     * Immutable navigation calculation result. Returned rectangles are defensive copies because
     * {@link Rectangle} is mutable.
     */
    public static final class Result {

        private final Rectangle navigationArea;
        private final Rectangle contentArea;

        private Result(Rectangle navigationArea, Rectangle contentArea) {
            this.navigationArea = new Rectangle(navigationArea);
            this.contentArea = new Rectangle(contentArea);
        }

        /**
         * @return a defensive copy of the collision-free navigation area
         */
        public Rectangle getNavigationArea() {
            return new Rectangle(navigationArea);
        }

        /**
         * @return a defensive copy of the full-width content area below navigation
         */
        public Rectangle getContentArea() {
            return new Rectangle(contentArea);
        }
    }
}
