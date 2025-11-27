# backend/analysis.py
import jieba
from wordcloud import WordCloud
from collections import Counter
from models import db, WordStat, Chapter  # Import Chapter
import os
import logging
from flask import current_app  # Import current_app to access config

logger = logging.getLogger(__name__)

# --- Font Path Configuration ---
# Assume font is bundled within the application, e.g., in an 'assets' folder
# IMPORTANT: Place your font file (e.g., NotoSansCJKsc-Regular.otf) here
DEFAULT_FONT_FILENAME = "MSYH.TTC"
FONT_PATH = os.path.join(os.path.dirname(__file__), "assets", DEFAULT_FONT_FILENAME)

if not os.path.exists(FONT_PATH):
    logger.warning(
        f"WordCloud font '{FONT_PATH}' not found. "
        "WordCloud generation might fail or use a default font (likely won't support CJK)."
        "Please place a suitable font file in the 'backend/assets/' directory."
    )
    # Set FONT_PATH to None to let WordCloud try its default, though it likely won't work for Chinese
    FONT_PATH = None
else:
    logger.info(f"WordCloud using font: {FONT_PATH}")
# --- End Font Path Configuration ---


def update_word_stats(novel_id: int):
    """
    Analyzes chapters from the database, updates word stats, and generates a word cloud.
    Now fetches content directly from the database.
    Returns the path to the generated word cloud image, or None if failed.
    """
    logger.info(f"Starting word stats update for novel_id: {novel_id}")

    # --- Fetch chapters from Database ---
    try:
        chapters_from_db = Chapter.query.filter_by(novel_id=novel_id).all()
        if not chapters_from_db:
            logger.warning(
                f"No chapters found in database for novel_id: {novel_id}. Skipping analysis."
            )
            return None
        # Create chapters dict {title: content}
        chapters_dict = {
            ch.title: ch.content for ch in chapters_from_db if ch.title and ch.content
        }
        logger.debug(
            f"Fetched {len(chapters_dict)} chapters with content from DB for novel_id: {novel_id}"
        )
    except Exception as e:
        logger.error(
            f"Failed to fetch chapters from database for novel_id {novel_id}: {e}",
            exc_info=True,
        )
        return None

    if not chapters_dict:
        logger.warning(
            f"No valid chapter content fetched from DB for novel_id: {novel_id}. Skipping analysis."
        )
        return None

    # 合并所有章节内容
    txt = "\n".join(chapters_dict.values())
    logger.debug(f"Total text length for analysis (novel_id {novel_id}): {len(txt)}")

    # 分词 (使用精确模式)
    try:
        # Consider adding a custom dictionary if needed for specific novel terms
        # jieba.load_userdict('path/to/your/dict.txt')
        words = [w for w in jieba.cut(txt) if len(w.strip()) > 1]
        logger.info(
            f"Segmented into {len(words)} words (length > 1) for novel_id: {novel_id}"
        )
    except Exception as e:
        logger.error(
            f"Jieba segmentation failed for novel_id {novel_id}: {e}", exc_info=True
        )
        return None

    # 计算词频
    # Consider adding a stopword list
    # stopwords = set([...])
    # words = [w for w in words if w not in stopwords]
    freq = Counter(words).most_common(300)  # Keep top 300 words
    logger.info(f"Calculated top {len(freq)} word frequencies for novel_id: {novel_id}")

    # 更新数据库
    try:
        # Use transaction for atomicity
        with db.session.begin_nested():  # Or db.session.begin() if no outer transaction
            # Clear previous stats for this novel
            WordStat.query.filter_by(novel_id=novel_id).delete()
            logger.debug(f"Deleted old word stats for novel_id: {novel_id}")

            # Bulk insert new stats
            stat_objects = [
                WordStat(novel_id=novel_id, word=w, freq=c) for w, c in freq
            ]
            if stat_objects:  # Only insert if there are words
                db.session.bulk_save_objects(stat_objects)
        db.session.commit()  # Commit the transaction
        logger.info(
            f"Saved {len(freq)} word stats to database for novel_id: {novel_id}"
        )
    except Exception as e:
        db.session.rollback()  # Rollback on error
        logger.error(
            f"Database operation failed for word stats (novel_id {novel_id}): {e}",
            exc_info=True,
        )
        return None  # Database operation failed

    # --- 生成词云图 ---
    # Get save path from Flask app config
    wordcloud_dir = current_app.config.get(
        "WORDCLOUD_SAVE_PATH",
        os.path.join(os.path.dirname(__file__), "data", "wordclouds"),
    )
    try:
        # Ensure the directory exists
        os.makedirs(wordcloud_dir, exist_ok=True)
    except OSError as e:
        logger.error(f"Failed to create wordcloud directory {wordcloud_dir}: {e}")
        return None  # Cannot save image

    img_path = os.path.join(wordcloud_dir, f"wordcloud_{novel_id}.png")

    if not freq:
        logger.warning(
            f"No words to generate wordcloud for novel_id: {novel_id}. Skipping image generation."
        )
        # Optionally delete old image if it exists
        if os.path.exists(img_path):
            try:
                os.remove(img_path)
            except OSError as e:
                logger.warning(f"Could not delete old wordcloud image {img_path}: {e}")
        return None  # Return None as no image was generated

    try:
        # Check if font path is valid before creating WordCloud
        if FONT_PATH is None:
            logger.error(
                f"Cannot generate wordcloud for novel_id {novel_id}: Font path is not set or font file is missing."
            )
            return None

        wc = WordCloud(
            font_path=FONT_PATH,  # Use the configured font path
            width=800,  # Increased size slightly
            height=600,
            background_color="white",
            max_words=200,  # Limit words displayed
            # prefer_horizontal=0.9, # Adjust layout if needed
            scale=1.5,  # Slightly higher resolution
        ).generate_from_frequencies(dict(freq))
        wc.to_file(img_path)
        logger.info(
            f"WordCloud image generated successfully for novel_id {novel_id} at: {img_path}"
        )
        return img_path  # Return the path of the generated image
    except ValueError as ve:
        # Catch specific error if font is not found by WordCloud internally
        if "font" in str(ve).lower():
            logger.error(
                f"WordCloud generation failed for novel_id {novel_id} due to font error: {ve}. Ensure '{FONT_PATH}' is a valid font file.",
                exc_info=True,
            )
        else:
            logger.error(
                f"WordCloud generation failed for novel_id {novel_id} with ValueError: {ve}",
                exc_info=True,
            )
        return None
    except Exception as e:
        logger.error(
            f"WordCloud generation failed for novel_id {novel_id}: {e}", exc_info=True
        )
        return None  # Word cloud generation failed, but stats might be saved
