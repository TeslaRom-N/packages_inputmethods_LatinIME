/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.PunctuationSuggestions;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethod.latin.utils.ViewLayoutUtils;

import java.util.ArrayList;

final class SuggestionStripLayoutHelper {
    private static final int DEFAULT_SUGGESTIONS_COUNT_IN_STRIP = 3;
    private static final float DEFAULT_CENTER_SUGGESTION_PERCENTILE = 0.40f;
    private static final int DEFAULT_MAX_MORE_SUGGESTIONS_ROW = 2;
    private static final int PUNCTUATIONS_IN_STRIP = 5;
    private static final float MIN_TEXT_XSCALE = 0.70f;

    public final int mPadding;
    public final int mDividerWidth;
    public final int mSuggestionsStripHeight;
    private final int mSuggestionsCountInStrip;
    public final int mMoreSuggestionsRowHeight;
    private int mMaxMoreSuggestionsRow;
    public final float mMinMoreSuggestionsWidth;
    public final int mMoreSuggestionsBottomGap;
    public boolean mMoreSuggestionsAvailable;

    // The index of these {@link ArrayList} is the position in the suggestion strip. The indices
    // increase towards the right for LTR scripts and the left for RTL scripts, starting with 0.
    // The position of the most important suggestion is in {@link #mCenterPositionInStrip}
    private final ArrayList<TextView> mWordViews;
    private final ArrayList<View> mDividerViews;
    private final ArrayList<TextView> mDebugInfoViews;

    private final int mColorValidTypedWord;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggested;
    private final float mAlphaObsoleted;
    private final float mCenterSuggestionWeight;
    private final int mCenterPositionInStrip;
    private final int mTypedWordPositionWhenAutocorrect;
    private final Drawable mMoreSuggestionsHint;
    private static final String MORE_SUGGESTIONS_HINT = "\u2026";
    private static final String LEFTWARDS_ARROW = "\u2190";
    private static final String RIGHTWARDS_ARROW = "\u2192";

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();

    private final int mSuggestionStripOptions;
    // These constants are the flag values of
    // {@link R.styleable#SuggestionStripView_suggestionStripOptions} attribute.
    private static final int AUTO_CORRECT_BOLD = 0x01;
    private static final int AUTO_CORRECT_UNDERLINE = 0x02;
    private static final int VALID_TYPED_WORD_BOLD = 0x04;

    public SuggestionStripLayoutHelper(final Context context, final AttributeSet attrs,
            final int defStyle, final ArrayList<TextView> wordViews,
            final ArrayList<View> dividerViews, final ArrayList<TextView> debugInfoViews) {
        mWordViews = wordViews;
        mDividerViews = dividerViews;
        mDebugInfoViews = debugInfoViews;

        final TextView wordView = wordViews.get(0);
        final View dividerView = dividerViews.get(0);
        mPadding = wordView.getCompoundPaddingLeft() + wordView.getCompoundPaddingRight();
        dividerView.measure(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDividerWidth = dividerView.getMeasuredWidth();

        final Resources res = wordView.getResources();
        mSuggestionsStripHeight = res.getDimensionPixelSize(
                R.dimen.config_suggestions_strip_height);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SuggestionStripView, defStyle, R.style.SuggestionStripView);
        mSuggestionStripOptions = a.getInt(
                R.styleable.SuggestionStripView_suggestionStripOptions, 0);
        mAlphaObsoleted = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaObsoleted, 1.0f);
        mColorValidTypedWord = a.getColor(R.styleable.SuggestionStripView_colorValidTypedWord, 0);
        mColorTypedWord = a.getColor(R.styleable.SuggestionStripView_colorTypedWord, 0);
        mColorAutoCorrect = a.getColor(R.styleable.SuggestionStripView_colorAutoCorrect, 0);
        mColorSuggested = a.getColor(R.styleable.SuggestionStripView_colorSuggested, 0);
        mSuggestionsCountInStrip = a.getInt(
                R.styleable.SuggestionStripView_suggestionsCountInStrip,
                DEFAULT_SUGGESTIONS_COUNT_IN_STRIP);
        mCenterSuggestionWeight = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_centerSuggestionPercentile,
                DEFAULT_CENTER_SUGGESTION_PERCENTILE);
        mMaxMoreSuggestionsRow = a.getInt(
                R.styleable.SuggestionStripView_maxMoreSuggestionsRow,
                DEFAULT_MAX_MORE_SUGGESTIONS_ROW);
        mMinMoreSuggestionsWidth = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_minMoreSuggestionsWidth, 1.0f);
        a.recycle();

        mMoreSuggestionsHint = getMoreSuggestionsHint(res,
                res.getDimension(R.dimen.config_more_suggestions_hint_text_size),
                mColorAutoCorrect);
        mCenterPositionInStrip = mSuggestionsCountInStrip / 2;
        // Assuming there are at least three suggestions. Also, note that the suggestions are
        // laid out according to script direction, so this is left of the center for LTR scripts
        // and right of the center for RTL scripts.
        mTypedWordPositionWhenAutocorrect = mCenterPositionInStrip - 1;
        mMoreSuggestionsBottomGap = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_bottom_gap);
        mMoreSuggestionsRowHeight = res.getDimensionPixelSize(
                R.dimen.config_more_suggestions_row_height);
    }

    public int getMaxMoreSuggestionsRow() {
        return mMaxMoreSuggestionsRow;
    }

    private int getMoreSuggestionsHeight() {
        return mMaxMoreSuggestionsRow * mMoreSuggestionsRowHeight + mMoreSuggestionsBottomGap;
    }

    public int setMoreSuggestionsHeight(final int remainingHeight) {
        final int currentHeight = getMoreSuggestionsHeight();
        if (currentHeight <= remainingHeight) {
            return currentHeight;
        }

        mMaxMoreSuggestionsRow = (remainingHeight - mMoreSuggestionsBottomGap)
                / mMoreSuggestionsRowHeight;
        final int newHeight = getMoreSuggestionsHeight();
        return newHeight;
    }

    private static Drawable getMoreSuggestionsHint(final Resources res, final float textSize,
            final int color) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(textSize);
        paint.setColor(color);
        final Rect bounds = new Rect();
        paint.getTextBounds(MORE_SUGGESTIONS_HINT, 0, MORE_SUGGESTIONS_HINT.length(), bounds);
        final int width = Math.round(bounds.width() + 0.5f);
        final int height = Math.round(bounds.height() + 0.5f);
        final Bitmap buffer = Bitmap.createBitmap(width, (height * 3 / 2), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawText(MORE_SUGGESTIONS_HINT, width / 2, height, paint);
        return new BitmapDrawable(res, buffer);
    }

    private CharSequence getStyledSuggestedWord(final SuggestedWords suggestedWords,
            final int indexInSuggestedWords) {
        if (indexInSuggestedWords >= suggestedWords.size()) {
            return null;
        }
        final String word = suggestedWords.getLabel(indexInSuggestedWords);
        // TODO: don't use the index to decide whether this is the auto-correction/typed word, as
        // this is brittle
        final boolean isAutoCorrection = suggestedWords.mWillAutoCorrect
                && indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION;
        final boolean isTypedWordValid = suggestedWords.mTypedWordValid
                && indexInSuggestedWords == SuggestedWords.INDEX_OF_TYPED_WORD;
        if (!isAutoCorrection && !isTypedWordValid) {
            return word;
        }

        final int len = word.length();
        final Spannable spannedWord = new SpannableString(word);
        final int options = mSuggestionStripOptions;
        if ((isAutoCorrection && (options & AUTO_CORRECT_BOLD) != 0)
                || (isTypedWordValid && (options & VALID_TYPED_WORD_BOLD) != 0)) {
            spannedWord.setSpan(BOLD_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        if (isAutoCorrection && (options & AUTO_CORRECT_UNDERLINE) != 0) {
            spannedWord.setSpan(UNDERLINE_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return spannedWord;
    }

    private int getPositionInSuggestionStrip(final int indexInSuggestedWords,
            final SuggestedWords suggestedWords) {
        final int indexToDisplayMostImportantSuggestion;
        final int indexToDisplaySecondMostImportantSuggestion;
        if (suggestedWords.mWillAutoCorrect) {
            indexToDisplayMostImportantSuggestion = SuggestedWords.INDEX_OF_AUTO_CORRECTION;
            indexToDisplaySecondMostImportantSuggestion = SuggestedWords.INDEX_OF_TYPED_WORD;
        } else {
            indexToDisplayMostImportantSuggestion = SuggestedWords.INDEX_OF_TYPED_WORD;
            indexToDisplaySecondMostImportantSuggestion = SuggestedWords.INDEX_OF_AUTO_CORRECTION;
        }
        if (indexInSuggestedWords == indexToDisplayMostImportantSuggestion) {
            return mCenterPositionInStrip;
        }
        if (indexInSuggestedWords == indexToDisplaySecondMostImportantSuggestion) {
            return mTypedWordPositionWhenAutocorrect;
        }
        // If neither of those, the order in the suggestion strip is the same as in SuggestedWords.
        return indexInSuggestedWords;
    }

    private int getSuggestionTextColor(final SuggestedWords suggestedWords,
            final int indexInSuggestedWords) {
        final int positionInStrip =
                getPositionInSuggestionStrip(indexInSuggestedWords, suggestedWords);
        // Use identity for strings, not #equals : it's the typed word if it's the same object
        final boolean isTypedWord =
                suggestedWords.getWord(indexInSuggestedWords) == suggestedWords.mTypedWord;

        final int color;
        if (positionInStrip == mCenterPositionInStrip && suggestedWords.mWillAutoCorrect) {
            color = mColorAutoCorrect;
        } else if (isTypedWord && suggestedWords.mTypedWordValid) {
            color = mColorValidTypedWord;
        } else if (isTypedWord) {
            color = mColorTypedWord;
        } else {
            color = mColorSuggested;
        }
        if (LatinImeLogger.sDBG && suggestedWords.size() > 1) {
            // If we auto-correct, then the autocorrection is in slot 0 and the typed word
            // is in slot 1.
            if (positionInStrip == mCenterPositionInStrip
                    && AutoCorrectionUtils.shouldBlockAutoCorrectionBySafetyNet(
                            suggestedWords.getLabel(SuggestedWords.INDEX_OF_AUTO_CORRECTION),
                            suggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD))) {
                return 0xFFFF0000;
            }
        }

        if (suggestedWords.mIsObsoleteSuggestions && !isTypedWord) {
            return applyAlpha(color, mAlphaObsoleted);
        }
        return color;
    }

    private static int applyAlpha(final int color, final float alpha) {
        final int newAlpha = (int)(Color.alpha(color) * alpha);
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void addDivider(final ViewGroup stripView, final View dividerView) {
        stripView.addView(dividerView);
        final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)dividerView.getLayoutParams();
        params.gravity = Gravity.CENTER;
    }

    /**
     * Layout suggestions to the suggestions strip. And returns the number of suggestions displayed
     * in the suggestions strip.
     *
     * @param suggestedWords suggestions to be shown in the suggestions strip.
     * @param stripView the suggestions strip view.
     * @param placerView the view where the debug info will be placed.
     * @return the number of suggestions displayed in the suggestions strip
     */
    public int layoutAndReturnSuggestionCountInStrip(final SuggestedWords suggestedWords,
            final ViewGroup stripView, final ViewGroup placerView) {
        if (suggestedWords.isPunctuationSuggestions()) {
            return layoutPunctuationSuggestionsAndReturnSuggestionCountInStrip(
                    (PunctuationSuggestions)suggestedWords, stripView);
        }

        setupWordViewsTextAndColor(suggestedWords, mSuggestionsCountInStrip);
        final TextView centerWordView = mWordViews.get(mCenterPositionInStrip);
        final int stripWidth = stripView.getWidth();
        final int centerWidth = getSuggestionWidth(mCenterPositionInStrip, stripWidth);
        final int countInStrip;
        if (suggestedWords.size() == 1 || getTextScaleX(centerWordView.getText(), centerWidth,
                centerWordView.getPaint()) < MIN_TEXT_XSCALE) {
            // Layout only the most relevant suggested word at the center of the suggestion strip
            // by consolidating all slots in the strip.
            countInStrip = 1;
            mMoreSuggestionsAvailable = (suggestedWords.size() > countInStrip);
            layoutWord(mCenterPositionInStrip, stripWidth - mPadding);
            stripView.addView(centerWordView);
            setLayoutWeight(centerWordView, 1.0f, ViewGroup.LayoutParams.MATCH_PARENT);
            if (SuggestionStripView.DBG) {
                layoutDebugInfo(mCenterPositionInStrip, placerView, stripWidth);
            }
        } else {
            countInStrip = mSuggestionsCountInStrip;
            mMoreSuggestionsAvailable = (suggestedWords.size() > countInStrip);
            int x = 0;
            for (int positionInStrip = 0; positionInStrip < countInStrip; positionInStrip++) {
                if (positionInStrip != 0) {
                    final View divider = mDividerViews.get(positionInStrip);
                    // Add divider if this isn't the left most suggestion in suggestions strip.
                    addDivider(stripView, divider);
                    x += divider.getMeasuredWidth();
                }

                final int width = getSuggestionWidth(positionInStrip, stripWidth);
                final TextView wordView = layoutWord(positionInStrip, width);
                stripView.addView(wordView);
                setLayoutWeight(wordView, getSuggestionWeight(positionInStrip),
                        ViewGroup.LayoutParams.MATCH_PARENT);
                x += wordView.getMeasuredWidth();

                if (SuggestionStripView.DBG) {
                    layoutDebugInfo(positionInStrip, placerView, x);
                }
            }
        }
        return countInStrip;
    }

    /**
     * Format appropriately the suggested word in {@link #mWordViews} specified by
     * <code>positionInStrip</code>. When the suggested word doesn't exist, the corresponding
     * {@link TextView} will be disabled and never respond to user interaction. The suggested word
     * may be shrunk or ellipsized to fit in the specified width.
     *
     * The <code>positionInStrip</code> argument is the index in the suggestion strip. The indices
     * increase towards the right for LTR scripts and the left for RTL scripts, starting with 0.
     * The position of the most important suggestion is in {@link #mCenterPositionInStrip}. This
     * usually doesn't match the index in <code>suggedtedWords</code> -- see
     * {@link #getPositionInSuggestionStrip(int,SuggestedWords)}.
     *
     * @param positionInStrip the position in the suggestion strip.
     * @param width the maximum width for layout in pixels.
     * @return the {@link TextView} containing the suggested word appropriately formatted.
     */
    private TextView layoutWord(final int positionInStrip, final int width) {
        final TextView wordView = mWordViews.get(positionInStrip);
        final CharSequence word = wordView.getText();
        if (positionInStrip == mCenterPositionInStrip && mMoreSuggestionsAvailable) {
            // TODO: This "more suggestions hint" should have a nicely designed icon.
            wordView.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, mMoreSuggestionsHint);
            // HACK: Align with other TextViews that have no compound drawables.
            wordView.setCompoundDrawablePadding(-mMoreSuggestionsHint.getIntrinsicHeight());
        } else {
            wordView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        // Disable this suggestion if the suggestion is null or empty.
        wordView.setEnabled(!TextUtils.isEmpty(word));
        final CharSequence text = getEllipsizedText(word, width, wordView.getPaint());
        final float scaleX = getTextScaleX(word, width, wordView.getPaint());
        wordView.setText(text); // TextView.setText() resets text scale x to 1.0.
        wordView.setTextScaleX(Math.max(scaleX, MIN_TEXT_XSCALE));
        return wordView;
    }

    private void layoutDebugInfo(final int positionInStrip, final ViewGroup placerView,
            final int x) {
        final TextView debugInfoView = mDebugInfoViews.get(positionInStrip);
        final CharSequence debugInfo = debugInfoView.getText();
        if (debugInfo == null) {
            return;
        }
        placerView.addView(debugInfoView);
        debugInfoView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int infoWidth = debugInfoView.getMeasuredWidth();
        final int y = debugInfoView.getMeasuredHeight();
        ViewLayoutUtils.placeViewAt(
                debugInfoView, x - infoWidth, y, infoWidth, debugInfoView.getMeasuredHeight());
    }

    private int getSuggestionWidth(final int positionInStrip, final int maxWidth) {
        final int paddings = mPadding * mSuggestionsCountInStrip;
        final int dividers = mDividerWidth * (mSuggestionsCountInStrip - 1);
        final int availableWidth = maxWidth - paddings - dividers;
        return (int)(availableWidth * getSuggestionWeight(positionInStrip));
    }

    private float getSuggestionWeight(final int positionInStrip) {
        if (positionInStrip == mCenterPositionInStrip) {
            return mCenterSuggestionWeight;
        }
        // TODO: Revisit this for cases of 5 or more suggestions
        return (1.0f - mCenterSuggestionWeight) / (mSuggestionsCountInStrip - 1);
    }

    private void setupWordViewsTextAndColor(final SuggestedWords suggestedWords,
            final int countInStrip) {
        // Clear all suggestions first
        for (int positionInStrip = 0; positionInStrip < countInStrip; ++positionInStrip) {
            mWordViews.get(positionInStrip).setText(null);
            // Make this inactive for touches in {@link #layoutWord(int,int)}.
            if (SuggestionStripView.DBG) {
                mDebugInfoViews.get(positionInStrip).setText(null);
            }
        }
        final int count = Math.min(suggestedWords.size(), countInStrip);
        for (int indexInSuggestedWords = 0; indexInSuggestedWords < count;
                indexInSuggestedWords++) {
            final int positionInStrip =
                    getPositionInSuggestionStrip(indexInSuggestedWords, suggestedWords);
            final TextView wordView = mWordViews.get(positionInStrip);
            // {@link TextView#getTag()} is used to get the index in suggestedWords at
            // {@link SuggestionStripView#onClick(View)}.
            wordView.setTag(indexInSuggestedWords);
            wordView.setText(getStyledSuggestedWord(suggestedWords, indexInSuggestedWords));
            wordView.setTextColor(getSuggestionTextColor(suggestedWords, indexInSuggestedWords));
            if (SuggestionStripView.DBG) {
                mDebugInfoViews.get(positionInStrip).setText(
                        suggestedWords.getDebugString(indexInSuggestedWords));
            }
        }
    }

    private int layoutPunctuationSuggestionsAndReturnSuggestionCountInStrip(
            final PunctuationSuggestions punctuationSuggestions, final ViewGroup stripView) {
        final int countInStrip = Math.min(punctuationSuggestions.size(), PUNCTUATIONS_IN_STRIP);
        for (int positionInStrip = 0; positionInStrip < countInStrip; positionInStrip++) {
            if (positionInStrip != 0) {
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, mDividerViews.get(positionInStrip));
            }

            final TextView wordView = mWordViews.get(positionInStrip);
            wordView.setEnabled(true);
            wordView.setTextColor(mColorAutoCorrect);
            // {@link TextView#getTag()} is used to get the index in suggestedWords at
            // {@link SuggestionStripView#onClick(View)}.
            wordView.setTag(positionInStrip);
            wordView.setText(punctuationSuggestions.getLabel(positionInStrip));
            wordView.setTextScaleX(1.0f);
            wordView.setCompoundDrawables(null, null, null, null);
            stripView.addView(wordView);
            setLayoutWeight(wordView, 1.0f, mSuggestionsStripHeight);
        }
        mMoreSuggestionsAvailable = (punctuationSuggestions.size() > countInStrip);
        return countInStrip;
    }

    public void layoutAddToDictionaryHint(final String word, final ViewGroup addToDictionaryStrip) {
        final int stripWidth = addToDictionaryStrip.getWidth();
        final int width = stripWidth - mDividerWidth - mPadding * 2;

        final TextView wordView = (TextView)addToDictionaryStrip.findViewById(R.id.word_to_save);
        wordView.setTextColor(mColorTypedWord);
        final int wordWidth = (int)(width * mCenterSuggestionWeight);
        final CharSequence wordToSave = getEllipsizedText(word, wordWidth, wordView.getPaint());
        final float wordScaleX = wordView.getTextScaleX();
        wordView.setText(wordToSave);
        wordView.setTextScaleX(wordScaleX);
        setLayoutWeight(wordView, mCenterSuggestionWeight, ViewGroup.LayoutParams.MATCH_PARENT);

        final TextView hintView = (TextView)addToDictionaryStrip.findViewById(
                R.id.hint_add_to_dictionary);
        hintView.setTextColor(mColorAutoCorrect);
        final boolean isRtlLanguage = (ViewCompat.getLayoutDirection(addToDictionaryStrip)
                == ViewCompat.LAYOUT_DIRECTION_RTL);
        final String arrow = isRtlLanguage ? RIGHTWARDS_ARROW : LEFTWARDS_ARROW;
        final Resources res = addToDictionaryStrip.getResources();
        final boolean isRtlSystem = SubtypeLocaleUtils.isRtlLanguage(res.getConfiguration().locale);
        final CharSequence hintText = res.getText(R.string.hint_add_to_dictionary);
        final String hintWithArrow = (isRtlLanguage == isRtlSystem)
                ? (arrow + hintText) : (hintText + arrow);
        final int hintWidth = width - wordWidth;
        hintView.setTextScaleX(1.0f); // Reset textScaleX.
        final float hintScaleX = getTextScaleX(hintWithArrow, hintWidth, hintView.getPaint());
        hintView.setText(hintWithArrow);
        hintView.setTextScaleX(hintScaleX);
        setLayoutWeight(
                hintView, 1.0f - mCenterSuggestionWeight, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void layoutImportantNotice(final View importantNoticeStrip,
            final String importantNoticeTitle) {
        final TextView titleView = (TextView)importantNoticeStrip.findViewById(
                R.id.important_notice_title);
        final int width = titleView.getWidth() - titleView.getPaddingLeft()
                - titleView.getPaddingRight();
        titleView.setTextColor(mColorAutoCorrect);
        titleView.setText(importantNoticeTitle);
        titleView.setTextScaleX(1.0f); // Reset textScaleX.
        final float titleScaleX = getTextScaleX(importantNoticeTitle, width, titleView.getPaint());
        titleView.setTextScaleX(titleScaleX);
    }

    static void setLayoutWeight(final View v, final float weight, final int height) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            final LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
            llp.weight = weight;
            llp.width = 0;
            llp.height = height;
        }
    }

    private static float getTextScaleX(final CharSequence text, final int maxWidth,
            final TextPaint paint) {
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        if (width <= maxWidth || maxWidth <= 0) {
            return 1.0f;
        }
        return maxWidth / (float)width;
    }

    private static CharSequence getEllipsizedText(final CharSequence text, final int maxWidth,
            final TextPaint paint) {
        if (text == null) {
            return null;
        }
        final float scaleX = getTextScaleX(text, maxWidth, paint);
        if (scaleX >= MIN_TEXT_XSCALE) {
            paint.setTextScaleX(scaleX);
            return text;
        }

        // Note that TextUtils.ellipsize() use text-x-scale as 1.0 if ellipsize is needed. To
        // get squeezed and ellipsized text, passes enlarged width (maxWidth / MIN_TEXT_XSCALE).
        final float upscaledWidth = maxWidth / MIN_TEXT_XSCALE;
        CharSequence ellipsized = TextUtils.ellipsize(
                text, paint, upscaledWidth, TextUtils.TruncateAt.MIDDLE);
        // For an unknown reason, ellipsized seems to return a text that does indeed fit inside the
        // passed width according to paint.measureText, but not according to paint.getTextWidths.
        // But when rendered, the text seems to actually take up as many pixels as returned by
        // paint.getTextWidths, hence problem.
        // To save this case, we compare the measured size of the new text, and if it's too much,
        // try it again removing the difference. This may still give a text too long by one or
        // two pixels so we take an additional 2 pixels cushion and call it a day.
        // TODO: figure out why getTextWidths and measureText don't agree with each other, and
        // remove the following code.
        final float ellipsizedTextWidth = getTextWidth(ellipsized, paint);
        if (upscaledWidth <= ellipsizedTextWidth) {
            ellipsized = TextUtils.ellipsize(
                    text, paint, upscaledWidth - (ellipsizedTextWidth - upscaledWidth) - 2,
                    TextUtils.TruncateAt.MIDDLE);
        }
        paint.setTextScaleX(MIN_TEXT_XSCALE);
        return ellipsized;
    }

    private static int getTextWidth(final CharSequence text, final TextPaint paint) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        final Typeface savedTypeface = paint.getTypeface();
        paint.setTypeface(getTextTypeface(text));
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        int width = 0;
        for (int i = 0; i < count; i++) {
            width += Math.round(widths[i] + 0.5f);
        }
        paint.setTypeface(savedTypeface);
        return width;
    }

    private static Typeface getTextTypeface(final CharSequence text) {
        if (!(text instanceof SpannableString)) {
            return Typeface.DEFAULT;
        }

        final SpannableString ss = (SpannableString)text;
        final StyleSpan[] styles = ss.getSpans(0, text.length(), StyleSpan.class);
        if (styles.length == 0) {
            return Typeface.DEFAULT;
        }

        if (styles[0].getStyle() == Typeface.BOLD) {
            return Typeface.DEFAULT_BOLD;
        }
        // TODO: BOLD_ITALIC, ITALIC case?
        return Typeface.DEFAULT;
    }
}
