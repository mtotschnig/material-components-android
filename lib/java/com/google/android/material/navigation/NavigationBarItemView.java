/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.material.navigation;

import com.google.android.material.R;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static com.google.android.material.navigation.NavigationBarView.ACTIVE_INDICATOR_WIDTH_MATCH_PARENT;
import static com.google.android.material.navigation.NavigationBarView.ACTIVE_INDICATOR_WIDTH_WRAP_CONTENT;
import static com.google.android.material.navigation.NavigationBarView.ITEM_ICON_GRAVITY_START;
import static com.google.android.material.navigation.NavigationBarView.ITEM_ICON_GRAVITY_TOP;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.widget.TooltipCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RestrictTo;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.PointerIconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.animation.AnimationUtils;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.internal.BaselineLayout;
import com.google.android.material.motion.MotionUtils;
import com.google.android.material.navigation.NavigationBarView.ItemGravity;
import com.google.android.material.navigation.NavigationBarView.ItemIconGravity;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.ripple.RippleUtils;

/**
 * Provides a view that will be used to render destination items inside a {@link
 * NavigationBarMenuView}.
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public abstract class NavigationBarItemView extends FrameLayout implements MenuView.ItemView {
  private static final int INVALID_ITEM_POSITION = -1;
  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

  private boolean initialized = false;
  private ColorStateList itemRippleColor;
  @Nullable Drawable itemBackground;
  private int itemPaddingTop;
  private int itemPaddingBottom;
  private int activeIndicatorLabelPadding;
  private float shiftAmount;
  private float scaleUpFactor;
  private float scaleDownFactor;

  private int labelVisibilityMode;
  private boolean isShifting;

  @NonNull private final LinearLayout contentContainer;
  @NonNull private final LinearLayout innerContentContainer;
  @NonNull private final View activeIndicatorView;
  @NonNull private final FrameLayout iconContainer;

  private final ImageView icon;
  private final BaselineLayout labelGroup;
  private final TextView smallLabel;
  private final TextView largeLabel;
  private int itemPosition = INVALID_ITEM_POSITION;
  @StyleRes private int activeTextAppearance = 0;

  @Nullable private MenuItemImpl itemData;

  @Nullable private ColorStateList iconTint;
  @Nullable private Drawable originalIconDrawable;
  @Nullable private Drawable wrappedIconDrawable;

  private static final ActiveIndicatorTransform ACTIVE_INDICATOR_LABELED_TRANSFORM =
      new ActiveIndicatorTransform();
  private static final ActiveIndicatorTransform ACTIVE_INDICATOR_UNLABELED_TRANSFORM =
      new ActiveIndicatorUnlabeledTransform();

  private ValueAnimator activeIndicatorAnimator;
  private ActiveIndicatorTransform activeIndicatorTransform = ACTIVE_INDICATOR_LABELED_TRANSFORM;
  private float activeIndicatorProgress = 0F;
  private boolean activeIndicatorEnabled = false;
  // The desired width of the indicator. This is not necessarily the actual size of the rendered
  // indicator depending on whether the width of this view is wide enough to accommodate the full
  // desired width.
  private int activeIndicatorDesiredWidth = 0;
  private int activeIndicatorDesiredHeight = 0;
  private int activeIndicatorExpandedDesiredWidth = ACTIVE_INDICATOR_WIDTH_WRAP_CONTENT;
  private int activeIndicatorExpandedDesiredHeight = 0;
  private boolean activeIndicatorResizeable = false;
  // The margin from the start and end of this view which the active indicator should respect. If
  // the indicator width is greater than the total width minus the horizontal margins, the active
  // indicator will assume the max width of the view's total width minus horizontal margins.
  private int activeIndicatorMarginHorizontal = 0;
  private int activeIndicatorExpandedMarginHorizontal = 0;

  @Nullable private BadgeDrawable badgeDrawable;

  @ItemIconGravity private int itemIconGravity;
  private int badgeFixedEdge = BadgeDrawable.BADGE_FIXED_EDGE_START;
  @ItemGravity private int itemGravity = NavigationBarView.ITEM_GRAVITY_TOP_CENTER;

  public NavigationBarItemView(@NonNull Context context) {
    super(context);

    LayoutInflater.from(context).inflate(getItemLayoutResId(), this, true);
    contentContainer = findViewById(R.id.navigation_bar_item_content_container);
    innerContentContainer = findViewById(R.id.navigation_bar_item_inner_content_container);

    activeIndicatorView = findViewById(R.id.navigation_bar_item_active_indicator_view);
    iconContainer = findViewById(R.id.navigation_bar_item_icon_container);
    icon = findViewById(R.id.navigation_bar_item_icon_view);
    labelGroup = findViewById(R.id.navigation_bar_item_labels_group);
    smallLabel = findViewById(R.id.navigation_bar_item_small_label_view);
    largeLabel = findViewById(R.id.navigation_bar_item_large_label_view);

    setBackgroundResource(getItemBackgroundResId());

    itemPaddingTop = getResources().getDimensionPixelSize(getItemDefaultMarginResId());
    itemPaddingBottom = labelGroup.getPaddingBottom();
    activeIndicatorLabelPadding = 0;

    // The labels used aren't always visible, so they are unreliable for accessibility. Instead,
    // the content description of the NavigationBarItemView should be used for accessibility.
    smallLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    largeLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    setFocusable(true);
    calculateTextScaleFactors(smallLabel.getTextSize(), largeLabel.getTextSize());
    activeIndicatorExpandedDesiredHeight = getResources().getDimensionPixelSize(
        R.dimen.m3_expressive_item_expanded_active_indicator_height_default);

    // TODO(b/138148581): Support displaying a badge on label-only bottom navigation views.
    innerContentContainer.addOnLayoutChangeListener(
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (icon.getVisibility() == VISIBLE) {
            tryUpdateBadgeBounds(icon);
          }
          // If item icon gravity is start, we want to update the active indicator width in a layout
          // change listener to keep the active indicator size up to date with the content width.
          if (itemIconGravity == ITEM_ICON_GRAVITY_START
              && activeIndicatorExpandedDesiredWidth == ACTIVE_INDICATOR_WIDTH_WRAP_CONTENT) {
            LayoutParams lp = (LayoutParams) innerContentContainer.getLayoutParams();
            int newWidth = right - left + lp.rightMargin + lp.leftMargin;
            LayoutParams indicatorParams = (LayoutParams) activeIndicatorView.getLayoutParams();
            int minWidth =
                min(
                    activeIndicatorDesiredWidth,
                    getMeasuredWidth() - (activeIndicatorMarginHorizontal * 2));
            indicatorParams.width = max(newWidth, minWidth);
            activeIndicatorView.setLayoutParams(indicatorParams);
          }
        });
  }

  @Override
  protected int getSuggestedMinimumWidth() {
    if (itemIconGravity == ITEM_ICON_GRAVITY_START) {
      // Badge widths are not included for the start icon gravity config as we only want to measure
      // the core content.
      LayoutParams innerContentParams = (LayoutParams) innerContentContainer.getLayoutParams();
      return innerContentContainer.getMeasuredWidth()
          + innerContentParams.leftMargin
          + innerContentParams.rightMargin;
    }
    LinearLayout.LayoutParams labelGroupParams =
        (LinearLayout.LayoutParams) labelGroup.getLayoutParams();
    int labelWidth =
        labelGroupParams.leftMargin + labelGroup.getMeasuredWidth() + labelGroupParams.rightMargin;

    return max(getSuggestedIconWidth(), labelWidth);
  }

  @Override
  protected int getSuggestedMinimumHeight() {
    LayoutParams contentContainerParams = (LayoutParams) contentContainer.getLayoutParams();
    return contentContainer.getMeasuredHeight()
        + contentContainerParams.topMargin
        + contentContainerParams.bottomMargin;
  }

  @Override
  public void initialize(@NonNull MenuItemImpl itemData, int menuType) {
    this.itemData = itemData;
    setCheckable(itemData.isCheckable());
    setChecked(itemData.isChecked());
    setEnabled(itemData.isEnabled());
    setIcon(itemData.getIcon());
    setTitle(itemData.getTitle());
    setId(itemData.getItemId());
    if (!TextUtils.isEmpty(itemData.getContentDescription())) {
      setContentDescription(itemData.getContentDescription());
    }

    CharSequence tooltipText =
        !TextUtils.isEmpty(itemData.getTooltipText())
            ? itemData.getTooltipText()
            : itemData.getTitle();

    // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || VERSION.SDK_INT > VERSION_CODES.M) {
      TooltipCompat.setTooltipText(this, tooltipText);
    }
    setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
    this.initialized = true;
  }

  /**
   * Remove state so this View can be reused.
   *
   * <p>Item Views are held in a pool and reused when the number of menu items to be shown changes.
   * This will be called when this View is released from the pool.
   *
   * @see NavigationBarMenuView#buildMenuView()
   */
  void clear() {
    this.removeBadge();
    this.itemData = null;
    this.activeIndicatorProgress = 0;
    this.initialized = false;
  }

  public void setItemPosition(int position) {
    itemPosition = position;
  }

  public int getItemPosition() {
    return itemPosition;
  }

  public void setShifting(boolean shifting) {
    if (isShifting != shifting) {
      isShifting = shifting;
      refreshChecked();
    }
  }

  public void setLabelVisibilityMode(@NavigationBarView.LabelVisibility int mode) {
    if (labelVisibilityMode != mode) {
      labelVisibilityMode = mode;
      updateActiveIndicatorTransform();
      updateActiveIndicatorLayoutParams(getWidth());
      refreshChecked();
    }
  }

  private void updateItemIconGravity() {
    int sideMargin = 0;
    int labelGroupTopMargin = activeIndicatorLabelPadding;
    int labelGroupSideMargin = 0;
    int sidePadding = 0;
    badgeFixedEdge = BadgeDrawable.BADGE_FIXED_EDGE_START;
    if (itemIconGravity == ITEM_ICON_GRAVITY_START) {
      sideMargin =
          getResources()
              .getDimensionPixelSize(R.dimen.m3_expressive_navigation_item_leading_trailing_space);
      labelGroupTopMargin = 0;
      labelGroupSideMargin = activeIndicatorLabelPadding;
      badgeFixedEdge = BadgeDrawable.BADGE_FIXED_EDGE_END;
      sidePadding = activeIndicatorExpandedMarginHorizontal;
      if (labelGroup.getParent() != innerContentContainer) {
        contentContainer.removeView(labelGroup);
        innerContentContainer.addView(labelGroup);
      }
    } else if (labelGroup.getParent() != contentContainer) {
      innerContentContainer.removeView(labelGroup);
      contentContainer.addView(labelGroup);
    }
    FrameLayout.LayoutParams contentContainerLp = (LayoutParams) contentContainer.getLayoutParams();
    contentContainerLp.gravity = itemGravity;
    FrameLayout.LayoutParams innerContentLp =
        (LayoutParams) innerContentContainer.getLayoutParams();
    innerContentLp.leftMargin = sideMargin;
    innerContentLp.rightMargin = sideMargin;
    LinearLayout.LayoutParams labelGroupLp =
        (LinearLayout.LayoutParams) labelGroup.getLayoutParams();
    labelGroupLp.rightMargin =
        getLayoutDirection() == LAYOUT_DIRECTION_RTL ? labelGroupSideMargin : 0;
    labelGroupLp.leftMargin =
        getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 0 : labelGroupSideMargin;
    labelGroupLp.topMargin = labelGroupTopMargin;
    setPadding(sidePadding, 0, sidePadding, 0);
    updateActiveIndicatorLayoutParams(getWidth());
  }

  public void setItemIconGravity(@ItemIconGravity int iconGravity) {
    if (itemIconGravity != iconGravity) {
      itemIconGravity = iconGravity;
      updateItemIconGravity();
      refreshItemBackground();
    }
  }

  @Override
  @Nullable
  public MenuItemImpl getItemData() {
    return itemData;
  }

  @Override
  public void setTitle(@Nullable CharSequence title) {
    smallLabel.setText(title);
    largeLabel.setText(title);
    if (itemData == null || TextUtils.isEmpty(itemData.getContentDescription())) {
      setContentDescription(title);
    }

    CharSequence tooltipText =
        itemData == null || TextUtils.isEmpty(itemData.getTooltipText())
            ? title
            : itemData.getTooltipText();
    // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || VERSION.SDK_INT > VERSION_CODES.M) {
      TooltipCompat.setTooltipText(this, tooltipText);
    }
  }

  @Override
  public void setCheckable(boolean checkable) {
    refreshDrawableState();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // Update the width of the active indicator to fit within the bounds of its parent. This is
    // needed when there is not enough width to accommodate the desired width of the indicator. Post
    // this update in order to wait for parent layout changes to actually take effect before
    // setting the new width.
    final int width = w;
    post(
        new Runnable() {
          @Override
          public void run() {
            updateActiveIndicatorLayoutParams(width);
          }
        });
  }

  private void updateActiveIndicatorTransform() {
    if (isActiveIndicatorResizeableAndUnlabeled()) {
      activeIndicatorTransform = ACTIVE_INDICATOR_UNLABELED_TRANSFORM;
    } else {
      activeIndicatorTransform = ACTIVE_INDICATOR_LABELED_TRANSFORM;
    }
  }

  /**
   * Update the active indicator for a given 0-1 value.
   *
   * @param progress 0 when the indicator should communicate an unselected state (typically gone), 1
   *     when the indicator should communicate a selected state (typically showing at its full width
   *     and height).
   * @param target The final value towards which progress is animating. This can be used to
   *     determine if the indicator is being unselected or selected.
   */
  private void setActiveIndicatorProgress(
      @FloatRange(from = 0F, to = 1F) float progress, float target) {
    activeIndicatorTransform.updateForProgress(progress, target, activeIndicatorView);
    activeIndicatorProgress = progress;
  }

  /** If the active indicator is enabled, animate from it's current state to it's new state. */
  private void maybeAnimateActiveIndicatorToProgress(
      @FloatRange(from = 0F, to = 1F) final float newProgress) {
    // If the active indicator is disabled or this view is in the process of being initialized,
    // jump the active indicator to it's final state.
    if (!activeIndicatorEnabled || !initialized || !isAttachedToWindow()) {
      setActiveIndicatorProgress(newProgress, newProgress);
      return;
    }

    if (activeIndicatorAnimator != null) {
      activeIndicatorAnimator.cancel();
      activeIndicatorAnimator = null;
    }
    activeIndicatorAnimator = ValueAnimator.ofFloat(activeIndicatorProgress, newProgress);
    activeIndicatorAnimator.addUpdateListener(
        new AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            float progress = (float) animation.getAnimatedValue();
            setActiveIndicatorProgress(progress, newProgress);
          }
        });
    activeIndicatorAnimator.setInterpolator(
        MotionUtils.resolveThemeInterpolator(
            getContext(),
            R.attr.motionEasingEmphasizedInterpolator,
            AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR));
    activeIndicatorAnimator.setDuration(
        MotionUtils.resolveThemeDuration(
            getContext(),
            R.attr.motionDurationLong2,
            getResources().getInteger(R.integer.material_motion_duration_long_1)));
    activeIndicatorAnimator.start();
  }

  /**
   * Refresh the state of this item if it has been initialized.
   *
   * <p>This is useful if parameters calculated based on this item's checked state (label
   * visibility, indicator state, icon position) have changed and should be recalculated.
   */
  private void refreshChecked() {
    if (itemData != null) {
      setChecked(itemData.isChecked());
    }
  }

  private void setLayoutConfigurationIconAndLabel(
      View visibleLabel, View invisibleLabel, float scaleFactor, float topMarginShift) {
    setViewMarginAndGravity(
        contentContainer,
        itemIconGravity == ITEM_ICON_GRAVITY_TOP ? (int) (itemPaddingTop + topMarginShift) : 0,
        0,
        itemGravity);
    setViewMarginAndGravity(
        innerContentContainer,
        0,
        0,
        itemIconGravity == ITEM_ICON_GRAVITY_TOP
            ? Gravity.CENTER
            : Gravity.START | Gravity.CENTER_VERTICAL);
    updateViewPaddingBottom(
        labelGroup, itemIconGravity == ITEM_ICON_GRAVITY_TOP ? itemPaddingBottom : 0);
    labelGroup.setVisibility(VISIBLE);
    setViewScaleValues(visibleLabel, 1f, 1f, VISIBLE);
    setViewScaleValues(invisibleLabel, scaleFactor, scaleFactor, INVISIBLE);
  }

  private void setLayoutConfigurationIconOnly() {
    setViewMarginAndGravity(contentContainer, itemPaddingTop, itemPaddingTop,
        itemIconGravity == ITEM_ICON_GRAVITY_TOP ? Gravity.CENTER : itemGravity);
    setViewMarginAndGravity(innerContentContainer, 0, 0, Gravity.CENTER);
    updateViewPaddingBottom(labelGroup, 0);
    labelGroup.setVisibility(GONE);
  }

  @Override
  public void setChecked(boolean checked) {
    largeLabel.setPivotX(largeLabel.getWidth() / 2);
    largeLabel.setPivotY(largeLabel.getBaseline());
    smallLabel.setPivotX(smallLabel.getWidth() / 2);
    smallLabel.setPivotY(smallLabel.getBaseline());

    float newIndicatorProgress = checked ? 1F : 0F;
    maybeAnimateActiveIndicatorToProgress(newIndicatorProgress);

    switch (labelVisibilityMode) {
      case NavigationBarView.LABEL_VISIBILITY_AUTO:
        if (isShifting) {
          if (checked) {
            setLayoutConfigurationIconAndLabel(largeLabel, smallLabel, scaleUpFactor, 0);
          } else {
            setLayoutConfigurationIconOnly();
          }
        } else {
          if (checked) {
            setLayoutConfigurationIconAndLabel(largeLabel, smallLabel, scaleUpFactor, shiftAmount);
          } else {
            setLayoutConfigurationIconAndLabel(smallLabel, largeLabel, scaleDownFactor, 0);
          }
        }
        break;

      case NavigationBarView.LABEL_VISIBILITY_SELECTED:
        if (checked) {
          setLayoutConfigurationIconAndLabel(largeLabel, smallLabel, scaleUpFactor, 0);
        } else {
          // Show icon only
          setLayoutConfigurationIconOnly();
        }
        break;

      case NavigationBarView.LABEL_VISIBILITY_LABELED:
        if (checked) {
          setLayoutConfigurationIconAndLabel(largeLabel, smallLabel, scaleUpFactor, shiftAmount);
        } else {
          setLayoutConfigurationIconAndLabel(smallLabel, largeLabel, scaleDownFactor, 0);
        }
        break;

      case NavigationBarView.LABEL_VISIBILITY_UNLABELED:
        setLayoutConfigurationIconOnly();
        break;

      default:
        break;
    }

    refreshDrawableState();

    // Set the item as selected to send an AccessibilityEvent.TYPE_VIEW_SELECTED from View, so that
    // the item is read out as selected.
    setSelected(checked);
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    if (badgeDrawable != null && badgeDrawable.isVisible()) {
      CharSequence customContentDescription = itemData.getTitle();
      if (!TextUtils.isEmpty(itemData.getContentDescription())) {
        customContentDescription = itemData.getContentDescription();
      }
      info.setContentDescription(
          customContentDescription + ", " + badgeDrawable.getContentDescription());
    }
    AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
    infoCompat.setCollectionItemInfo(
        CollectionItemInfoCompat.obtain(
            /* rowIndex= */ 0,
            /* rowSpan= */ 1,
            /* columnIndex= */ getItemVisiblePosition(),
            /* columnSpan= */ 1,
            /* heading= */ false,
            /* selected= */ isSelected()));
    if (isSelected()) {
      infoCompat.setClickable(false);
      infoCompat.removeAction(AccessibilityActionCompat.ACTION_CLICK);
    }
    infoCompat.setRoleDescription(getResources().getString(R.string.item_view_role_description));
  }

  /**
   * Iterate through all the preceding bottom navigating items to determine this item's visible
   * position.
   *
   * @return This item's visible position in a bottom navigation.
   */
  private int getItemVisiblePosition() {
    ViewGroup parent = (ViewGroup) getParent();
    int index = parent.indexOfChild(this);
    int visiblePosition = 0;
    for (int i = 0; i < index; i++) {
      View child = parent.getChildAt(i);
      if (child instanceof NavigationBarItemView && child.getVisibility() == View.VISIBLE) {
        visiblePosition++;
      }
    }
    return visiblePosition;
  }

  private static void setViewMarginAndGravity(
      @NonNull View view, int topMargin, int bottomMargin, int gravity) {
    LayoutParams viewParams = (LayoutParams) view.getLayoutParams();
    viewParams.topMargin = topMargin;
    viewParams.bottomMargin = bottomMargin;
    viewParams.gravity = gravity;
    view.setLayoutParams(viewParams);
  }

  private static void setViewScaleValues(
      @NonNull View view, float scaleX, float scaleY, int visibility) {
    view.setScaleX(scaleX);
    view.setScaleY(scaleY);
    view.setVisibility(visibility);
  }

  private static void updateViewPaddingBottom(@NonNull View view, int paddingBottom) {
    view.setPadding(
        view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), paddingBottom);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    smallLabel.setEnabled(enabled);
    largeLabel.setEnabled(enabled);
    icon.setEnabled(enabled);

    if (enabled) {
      ViewCompat.setPointerIcon(
          this, PointerIconCompat.getSystemIcon(getContext(), PointerIconCompat.TYPE_HAND));
    } else {
      ViewCompat.setPointerIcon(this, null);
    }
  }

  @Override
  @NonNull
  public int[] onCreateDrawableState(final int extraSpace) {
    final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (itemData != null && itemData.isCheckable() && itemData.isChecked()) {
      mergeDrawableStates(drawableState, CHECKED_STATE_SET);
    }
    return drawableState;
  }

  @Override
  public void setShortcut(boolean showShortcut, char shortcutKey) {}

  @Override
  public void setIcon(@Nullable Drawable iconDrawable) {
    if (iconDrawable == originalIconDrawable) {
      return;
    }

    // Save the original icon to check if it has changed in future calls of this method.
    originalIconDrawable = iconDrawable;
    if (iconDrawable != null) {
      Drawable.ConstantState state = iconDrawable.getConstantState();
      iconDrawable =
          DrawableCompat.wrap(state == null ? iconDrawable : state.newDrawable()).mutate();
      wrappedIconDrawable = iconDrawable;
      if (iconTint != null) {
        DrawableCompat.setTintList(wrappedIconDrawable, iconTint);
      }
    }
    this.icon.setImageDrawable(iconDrawable);
  }

  @Override
  public boolean prefersCondensedTitle() {
    return false;
  }

  @Override
  public boolean showsIcon() {
    return true;
  }

  public void setIconTintList(@Nullable ColorStateList tint) {
    iconTint = tint;
    if (itemData != null && wrappedIconDrawable != null) {
      DrawableCompat.setTintList(wrappedIconDrawable, iconTint);
      wrappedIconDrawable.invalidateSelf();
    }
  }

  public void setIconSize(int iconSize) {
    LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon.getLayoutParams();
    iconParams.width = iconSize;
    iconParams.height = iconSize;
    icon.setLayoutParams(iconParams);
  }

  // TODO(b/338647654): We can remove this once navigation rail is updated
  public void setMeasureBottomPaddingFromLabelBaseline(boolean measurePaddingFromBaseline) {
    labelGroup.setMeasurePaddingFromBaseline(measurePaddingFromBaseline);
    smallLabel.setIncludeFontPadding(measurePaddingFromBaseline);
    largeLabel.setIncludeFontPadding(measurePaddingFromBaseline);
    requestLayout();
  }

  public void setTextAppearanceInactive(@StyleRes int inactiveTextAppearance) {
    setTextAppearanceWithoutFontScaling(smallLabel, inactiveTextAppearance);
    calculateTextScaleFactors(smallLabel.getTextSize(), largeLabel.getTextSize());
    smallLabel.setMinimumHeight(
        MaterialResources.getUnscaledLineHeight(
            smallLabel.getContext(), inactiveTextAppearance, 0));
  }

  public void setTextAppearanceActive(@StyleRes int activeTextAppearance) {
    this.activeTextAppearance = activeTextAppearance;
    setTextAppearanceWithoutFontScaling(largeLabel, activeTextAppearance);
    calculateTextScaleFactors(smallLabel.getTextSize(), largeLabel.getTextSize());
    largeLabel.setMinimumHeight(
        MaterialResources.getUnscaledLineHeight(largeLabel.getContext(), activeTextAppearance, 0));
  }

  public void setTextAppearanceActiveBoldEnabled(boolean isBold) {
    setTextAppearanceActive(activeTextAppearance);
    // TODO(b/246765947): Use component tokens to control font weight
    largeLabel.setTypeface(largeLabel.getTypeface(), isBold ? Typeface.BOLD : Typeface.NORMAL);
  }

  /**
   * Remove font scaling if the text size is in scaled pixels.
   *
   * <p>Labels are instead made accessible by showing a scaled tooltip on long press of a
   * destination. If the given {@code textAppearance} is 0 or does not have a textSize, this method
   * will not remove the existing scaling from the {@code textView}.
   */
  private static void setTextAppearanceWithoutFontScaling(
      TextView textView, @StyleRes int textAppearance) {
    TextViewCompat.setTextAppearance(textView, textAppearance);
    int unscaledSize =
        MaterialResources.getUnscaledTextSize(textView.getContext(), textAppearance, 0);
    if (unscaledSize != 0) {
      textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, unscaledSize);
    }
  }

  public void setTextColor(@Nullable ColorStateList color) {
    if (color != null) {
      smallLabel.setTextColor(color);
      largeLabel.setTextColor(color);
    }
  }

  private void calculateTextScaleFactors(float smallLabelSize, float largeLabelSize) {
    shiftAmount = smallLabelSize - largeLabelSize;
    scaleUpFactor = 1f * largeLabelSize / smallLabelSize;
    scaleDownFactor = 1f * smallLabelSize / largeLabelSize;
  }

  public void setItemBackground(int background) {
    Drawable backgroundDrawable =
        background == 0 ? null : ContextCompat.getDrawable(getContext(), background);
    setItemBackground(backgroundDrawable);
  }

  public void setItemBackground(@Nullable Drawable background) {
    if (background != null && background.getConstantState() != null) {
      background = background.getConstantState().newDrawable().mutate();
    }
    this.itemBackground = background;
    refreshItemBackground();
  }

  public void setItemRippleColor(@Nullable ColorStateList itemRippleColor) {
    this.itemRippleColor = itemRippleColor;
    refreshItemBackground();
  }

  /**
   * Update this item's ripple behavior given the current configuration.
   *
   * <p>If an active indicator is being used, a ripple is added to the active indicator. Otherwise,
   * if a custom background has not been set, a default background that works across all API levels
   * is created and set.
   */
  private void refreshItemBackground() {
    Drawable iconContainerRippleDrawable = null;
    Drawable itemBackgroundDrawable = itemBackground;
    boolean defaultHighlightEnabled = true;

    if (itemRippleColor != null) {
      Drawable maskDrawable = getActiveIndicatorDrawable();
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
          && activeIndicatorEnabled
          && getActiveIndicatorDrawable() != null
          && maskDrawable != null) {

        // Remove the default focus highlight that highlights the entire view and rely on the
        // active indicator ripple to communicate state.
        defaultHighlightEnabled = false;
        // Set the icon container's foreground to a ripple masked by the active indicator's
        // drawable.
        iconContainerRippleDrawable =
            new RippleDrawable(
                RippleUtils.sanitizeRippleDrawableColor(itemRippleColor), null, maskDrawable);
      } else if (itemBackgroundDrawable == null) {
        // If there has not been a custom background set, use a fallback item background to display
        // state over the entire item.
        itemBackgroundDrawable = createItemBackgroundCompat(itemRippleColor);
      }
    }
    // Remove any padding to avoid the active indicator from from being clipped
    iconContainer.setPadding(0, 0, 0, 0);
    iconContainer.setForeground(iconContainerRippleDrawable);
    setBackground(itemBackgroundDrawable);
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      setDefaultFocusHighlightEnabled(defaultHighlightEnabled);
    }
  }

  /**
   * Create a {@link Drawable} to be used as this item's background when a an active indicator is
   * not in use or a custom item background has not been set.
   *
   * @return a {@link Drawable} that can be used as a background and display state.
   */
  private static Drawable createItemBackgroundCompat(@NonNull ColorStateList rippleColor) {
    ColorStateList rippleDrawableColor = RippleUtils.convertToRippleDrawableColor(rippleColor);
    Drawable backgroundDrawable;
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      backgroundDrawable = new RippleDrawable(rippleDrawableColor, null, null);
    } else {
      GradientDrawable rippleDrawable = new GradientDrawable();
      // TODO: Find a workaround for this. Currently on certain devices/versions, LayerDrawable
      // will draw a black background underneath any layer with a non-opaque color,
      // (e.g. ripple) unless we set the shape to be something that's not a perfect rectangle.
      rippleDrawable.setCornerRadius(0.00001F);
      Drawable rippleDrawableCompat = DrawableCompat.wrap(rippleDrawable);
      DrawableCompat.setTintList(rippleDrawableCompat, rippleDrawableColor);
      backgroundDrawable = rippleDrawableCompat;
    }
    return backgroundDrawable;
  }

  /**
   * Set the padding applied to the icon/active indicator container from the top of the item view.
   */
  public void setItemPaddingTop(int paddingTop) {
    if (this.itemPaddingTop != paddingTop) {
      this.itemPaddingTop = paddingTop;
      refreshChecked();
    }
  }

  /** Set the padding applied to the labels from the bottom of the item view. */
  public void setItemPaddingBottom(int paddingBottom) {
    if (this.itemPaddingBottom != paddingBottom) {
      this.itemPaddingBottom = paddingBottom;
      refreshChecked();
    }
  }

  /** Set the padding between the active indicator container and the item label. */
  public void setActiveIndicatorLabelPadding(int activeIndicatorLabelPadding) {
    if (this.activeIndicatorLabelPadding != activeIndicatorLabelPadding) {
      this.activeIndicatorLabelPadding = activeIndicatorLabelPadding;
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) labelGroup.getLayoutParams();
      lp.topMargin = activeIndicatorLabelPadding;
      requestLayout();
    }
  }

  /** Set whether or not this item should show an active indicator when checked. */
  public void setActiveIndicatorEnabled(boolean enabled) {
    this.activeIndicatorEnabled = enabled;
    refreshItemBackground();
    activeIndicatorView.setVisibility(enabled ? View.VISIBLE : View.GONE);
    requestLayout();
  }

  /**
   * Set the gravity of the item.
   */
  public void setItemGravity(@ItemGravity int itemGravity) {
    this.itemGravity = itemGravity;
    requestLayout();
  }

  /**
   * Set the desired width of the active indicator.
   *
   * <p>If the item view is not wide enough to accommodate the given {@code width} plus any
   * horizontal margin, the width will be set to the width of the item view minus any horizontal
   * margin.
   *
   * @param width The desired width of the active indicator.
   */
  public void setActiveIndicatorWidth(int width) {
    this.activeIndicatorDesiredWidth = width;
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /**
   * Set the height of the active indicator when it is expanded, ie. the item icon width is set to
   * {@link ItemIconGravity#ITEM_ICON_GRAVITY_START}.
   *
   * @param width The width of the active indicator.
   */
  public void setActiveIndicatorExpandedWidth(int width) {
    this.activeIndicatorExpandedDesiredWidth = width;
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /**
   * Set the height of the active indicator when it is expanded, ie. the item icon width is set to *
   * {@link ItemIconGravity#ITEM_ICON_GRAVITY_START}.
   *
   * @param height The height of the active indicator.
   */
  public void setActiveIndicatorExpandedHeight(int height) {
    this.activeIndicatorExpandedDesiredHeight = height;
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /**
   * Update the active indicators width and height for the available width and label visibility
   * mode.
   *
   * @param availableWidth The total width of this item layout.
   */
  private void updateActiveIndicatorLayoutParams(int availableWidth) {
    // Set width to the min of either the desired indicator width or the available width minus
    // a horizontal margin.
    if (availableWidth <= 0) {
      return;
    }

    int newWidth =
        min(activeIndicatorDesiredWidth, availableWidth - (activeIndicatorMarginHorizontal * 2));
    int newHeight = activeIndicatorDesiredHeight;
    if (itemIconGravity == ITEM_ICON_GRAVITY_START) {
      int adjustedAvailableWidth = availableWidth - (activeIndicatorExpandedMarginHorizontal * 2);
      if (activeIndicatorExpandedDesiredWidth == ACTIVE_INDICATOR_WIDTH_MATCH_PARENT) {
        newWidth = adjustedAvailableWidth;
      } else if (activeIndicatorExpandedDesiredWidth == ACTIVE_INDICATOR_WIDTH_WRAP_CONTENT) {
        newWidth = contentContainer.getMeasuredWidth();
      } else {
        newWidth = min(activeIndicatorExpandedDesiredWidth, adjustedAvailableWidth);
      }
      newHeight = activeIndicatorExpandedDesiredHeight;
    }
    LayoutParams indicatorParams = (LayoutParams) activeIndicatorView.getLayoutParams();
    // If the label visibility is unlabeled, make the active indicator's height equal to its
    // width.
    indicatorParams.height = isActiveIndicatorResizeableAndUnlabeled() ? newWidth : newHeight;
    indicatorParams.width = newWidth;
    activeIndicatorView.setLayoutParams(indicatorParams);
  }

  private boolean isActiveIndicatorResizeableAndUnlabeled() {
    return activeIndicatorResizeable
        && labelVisibilityMode == NavigationBarView.LABEL_VISIBILITY_UNLABELED;
  }

  /**
   * Set the desired height of the active indicator.
   *
   * <p>TODO: Consider adjusting based on available height
   *
   * @param height The desired height of the active indicator.
   */
  public void setActiveIndicatorHeight(int height) {
    activeIndicatorDesiredHeight = height;
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /**
   * Set the horizontal margin that will be maintained at the start and end of the active indicator,
   * making sure the indicator remains the given distance from the edge of this item view.
   *
   * @see #updateActiveIndicatorLayoutParams(int)
   * @param marginHorizontal The horizontal margin, in pixels.
   */
  public void setActiveIndicatorMarginHorizontal(@Px int marginHorizontal) {
    this.activeIndicatorMarginHorizontal = marginHorizontal;
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /**
   * Set the horizontal margin that will be maintained at the start and end of the expanded active
   * indicator, making sure the indicator remains the given distance from the edge of this item
   * view.
   *
   * @see #updateActiveIndicatorLayoutParams(int)
   * @param marginHorizontal The horizontal margin, in pixels.
   */
  public void setActiveIndicatorExpandedMarginHorizontal(@Px int marginHorizontal) {
    this.activeIndicatorExpandedMarginHorizontal = marginHorizontal;
    if (itemIconGravity == ITEM_ICON_GRAVITY_START) {
      setPadding(marginHorizontal, 0, marginHorizontal, 0);
    }
    updateActiveIndicatorLayoutParams(getWidth());
  }

  /** Get the drawable used as the active indicator. */
  @Nullable
  public Drawable getActiveIndicatorDrawable() {
    return activeIndicatorView.getBackground();
  }

  /**
   * Set the drawable to be used as the active indicator that contains the icon in the {@link
   * ItemIconGravity#ITEM_ICON_GRAVITY_TOP} configuration.
   */
  public void setActiveIndicatorDrawable(@Nullable Drawable activeIndicatorDrawable) {
    activeIndicatorView.setBackground(activeIndicatorDrawable);
    refreshItemBackground();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    // Pass touch events through to the icon container so the active indicator
    // ripple can be shown.
    if (activeIndicatorEnabled) {
      iconContainer.dispatchTouchEvent(ev);
    }
    return super.dispatchTouchEvent(ev);
  }

  /** Set whether the indicator can be automatically resized. */
  public void setActiveIndicatorResizeable(boolean resizeable) {
    this.activeIndicatorResizeable = resizeable;
  }

  void setBadge(@NonNull BadgeDrawable badgeDrawable) {
    if (this.badgeDrawable == badgeDrawable) {
      return;
    }
    if (hasBadge() && icon != null) {
      Log.w("NavigationBar", "Multiple badges shouldn't be attached to one item.");
      tryRemoveBadgeFromAnchor(icon);
    }
    this.badgeDrawable = badgeDrawable;
    badgeDrawable.setBadgeFixedEdge(badgeFixedEdge);
    if (icon != null) {
      tryAttachBadgeToAnchor(icon);
    }
  }

  @Nullable
  public BadgeDrawable getBadge() {
    return this.badgeDrawable;
  }

  void removeBadge() {
    tryRemoveBadgeFromAnchor(icon);
  }

  private boolean hasBadge() {
    return badgeDrawable != null;
  }

  private void tryUpdateBadgeBounds(View anchorView) {
    if (!hasBadge()) {
      return;
    }
    BadgeUtils.setBadgeDrawableBounds(badgeDrawable, anchorView, null);
  }

  private void tryAttachBadgeToAnchor(@Nullable View anchorView) {
    if (!hasBadge()) {
      return;
    }
    if (anchorView != null) {
      // Avoid clipping a badge if it's displayed.
      setClipChildren(false);
      setClipToPadding(false);

      BadgeUtils.attachBadgeDrawable(badgeDrawable, anchorView);
    }
  }

  private void tryRemoveBadgeFromAnchor(@Nullable View anchorView) {
    if (!hasBadge()) {
      return;
    }
    if (anchorView != null) {
      // Clip children / view to padding when no badge is displayed.
      setClipChildren(true);
      setClipToPadding(true);

      BadgeUtils.detachBadgeDrawable(badgeDrawable, anchorView);
    }
    badgeDrawable = null;
  }

  private int getSuggestedIconWidth() {
    int badgeWidth =
        badgeDrawable == null
            ? 0
            : badgeDrawable.getMinimumWidth() - badgeDrawable.getHorizontalOffset();

    // Account for the fact that the badge may fit within the left or right margin. Give the same
    // space of either side so that icon position does not move if badge gravity is changed.
    LinearLayout.LayoutParams iconContainerParams =
        (LinearLayout.LayoutParams) iconContainer.getLayoutParams();
    return max(badgeWidth, iconContainerParams.leftMargin)
        + icon.getMeasuredWidth()
        + max(badgeWidth, iconContainerParams.rightMargin);
  }

  /**
   * Returns the unique identifier to the drawable resource that must be used to render background
   * of the menu item view. Override this if the subclassed menu item requires a different
   * background resource to be set.
   */
  @DrawableRes
  protected int getItemBackgroundResId() {
    return R.drawable.mtrl_navigation_bar_item_background;
  }

  /**
   * Returns the unique identifier to the dimension resource that will specify the default margin
   * this menu item view. Override this if the subclassed menu item requires a different default
   * margin value.
   */
  @DimenRes
  protected int getItemDefaultMarginResId() {
    return R.dimen.mtrl_navigation_bar_item_default_margin;
  }

  /**
   * Returns the unique identifier to the layout resource that must be used to render the items in
   * this menu item view.
   */
  @LayoutRes
  protected abstract int getItemLayoutResId();

  /**
   * A class used to manipulate the {@link NavigationBarItemView}'s active indicator view when
   * animating between hidden and shown.
   *
   * <p>By default, this class scales the indicator in the x direction to reveal the default pill
   * shape.
   *
   * <p>Subclasses can override {@link #updateForProgress(float, float, View)} to manipulate the
   * view in any way appropriate.
   */
  private static class ActiveIndicatorTransform {

    private static final float SCALE_X_HIDDEN = .4F;
    private static final float SCALE_X_SHOWN = 1F;

    // The fraction of the animation's total duration over which the indicator will be faded in or
    // out.
    private static final float ALPHA_FRACTION = 1F / 5F;

    /**
     * Calculate the alpha value, based on a progress and target value, that has the indicator
     * appear or disappear over the first 1/5th of the transform.
     */
    protected float calculateAlpha(
        @FloatRange(from = 0F, to = 1F) float progress,
        @FloatRange(from = 0F, to = 1F) float targetValue) {
      // Animate the alpha of the indicator over the first ALPHA_FRACTION of the animation
      float startAlphaFraction = targetValue == 0F ? 1F - ALPHA_FRACTION : 0F;
      float endAlphaFraction = targetValue == 0F ? 1F : 0F + ALPHA_FRACTION;
      return AnimationUtils.lerp(0F, 1F, startAlphaFraction, endAlphaFraction, progress);
    }

    protected float calculateScaleX(
        @FloatRange(from = 0F, to = 1F) float progress,
        @FloatRange(from = 0F, to = 1F) float targetValue) {
      return AnimationUtils.lerp(SCALE_X_HIDDEN, SCALE_X_SHOWN, progress);
    }

    protected float calculateScaleY(
        @FloatRange(from = 0F, to = 1F) float progress,
        @FloatRange(from = 0F, to = 1F) float targetValue) {
      return 1F;
    }

    /**
     * Called whenever the {@code indicator} should update its parameters (scale, alpha, etc.) in
     * response to a change in progress.
     *
     * @param progress A value between 0 and 1 where 0 represents a fully hidden indicator and 1
     *     indicates a fully shown indicator.
     * @param targetValue The final value towards which the progress is moving. This will be either
     *     0 and 1 and can be used to determine whether the indicator is showing or hiding if show
     *     and hide animations differ.
     * @param indicator The active indicator {@link View}.
     */
    public void updateForProgress(
        @FloatRange(from = 0F, to = 1F) float progress,
        @FloatRange(from = 0F, to = 1F) float targetValue,
        @NonNull View indicator) {
      indicator.setScaleX(calculateScaleX(progress, targetValue));
      indicator.setScaleY(calculateScaleY(progress, targetValue));
      indicator.setAlpha(calculateAlpha(progress, targetValue));
    }
  }

  /**
   * A transform class used to animate the active indicator of a {@link NavigationBarItemView} that
   * is unlabeled.
   *
   * <p>This differs from the default {@link ActiveIndicatorTransform} class by uniformly scaling in
   * the X and Y axis.
   */
  private static class ActiveIndicatorUnlabeledTransform extends ActiveIndicatorTransform {

    @Override
    protected float calculateScaleY(float progress, float targetValue) {
      return calculateScaleX(progress, targetValue);
    }
  }
}
