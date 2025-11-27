# backend/novel_downloader/novel_src/book_parser/book_manager.py
import os
import json
from pathlib import Path
from typing import Dict, List, Tuple, Any, Optional

from ..base_system.context import GlobalContext
from ..base_system.storge_system import FileCleaner
from .epub_generator import EpubGenerator  # Keep this for potential future use


class BookManager(object):
    """书籍存储控制器 (Adapted for library use)"""

    def __init__(
        self,
        book_id: str,
        book_name: str,
        author: str,
        tags: list,
        description: str,
        # save_path and status_folder are now derived from Config
    ):
        # Book info cache
        self.book_id = book_id
        self.book_name = book_name
        self.author = author
        self.end = bool(tags and tags[0] == "已完结")
        self.tags = "|".join(tags) if tags else ""
        self.description = description

        # Initialization from GlobalContext
        self.config = GlobalContext.get_config()
        self.logger = GlobalContext.get_logger()

        # Determine paths from Config
        # The specific status folder for this book needs book_name and book_id
        self.status_folder = self.config.status_folder_path(
            self.book_name, self.book_id
        )
        self.save_dir = self.config.default_save_dir  # Base save dir

        # Cache for downloaded chapters {chapter_id: [title, content/error_marker]}
        self.downloaded: Dict[str, List[Any]] = {}

        # Status file path
        filename = f"chapter_status_{self.book_id}.json"
        self.status_file = self.status_folder / filename

        self._load_download_status()  # Load previous status if exists

    def _load_download_status(self):
        """加载完整的下载状态 (No changes needed here conceptually)"""
        try:
            if self.status_file.exists():
                with self.status_file.open("r", encoding="utf-8") as f:
                    data = json.load(f)
                    # Load metadata from status file IF it wasn't passed initially?
                    # For library use, assume initial metadata is correct.
                    # self.book_name = data.get("book_name", self.book_name)
                    # self.author = data.get("author", self.author)
                    # self.tags = data.get("tags", self.tags)
                    # self.description = data.get("description", self.description)
                    self.downloaded = data.get("downloaded", {})
                    self.logger.info(
                        f"Loaded {len(self.downloaded)} chapter statuses from {self.status_file}"
                    )
        except Exception as e:
            self.logger.warning(f"Status file {self.status_file} loading failed: {e}")
            self.downloaded = {}

    def save_chapter(self, chapter_id: str, title: str, content: str):
        """Stores chapter content in memory. Optionally writes bulk files if configured."""
        if not title:  # Add check for empty title
            self.logger.warning(
                f"Chapter {chapter_id} has empty title, using Chapter ID as title."
            )
            title = f"Chapter {chapter_id}"
        if not content:  # Handle empty content - store as error? Or skip?
            self.logger.warning(
                f"Chapter {chapter_id} ('{title}') received empty content. Storing as error."
            )
            self.save_error_chapter(chapter_id, title, "Empty Content")
            return

        self.downloaded[chapter_id] = [title, content]
        self.logger.debug(f"Chapter {chapter_id} ('{title[:20]}...') cached in memory.")

        # Optional: Keep bulk file saving if needed, controlled by config
        if self.config.bulk_files:
            try:
                bulk_dir = (
                    self.save_dir / self.book_name
                )  # Use base save dir + book name
                bulk_dir.mkdir(parents=True, exist_ok=True)

                if self.config.novel_format == "epub":
                    suffix = ".xhtml"
                    # Generate simple XHTML content
                    # Ensure title and content are properly escaped if needed for XML
                    # Basic escaping:
                    safe_title_xml = (
                        title.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    )
                    # Content might already be HTML-like, handle carefully
                    # For now, assume content is plain text or simple HTML paragraph
                    # A more robust solution would use an HTML library for generation
                    xhtml_content = f"<p>{content.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')}</p>"  # Basic wrapping
                    xhtml_template = f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>{safe_title_xml}</title>
</head>
<body>
<h1>{safe_title_xml}</h1>
{xhtml_content}
</body>
</html>"""
                    file_content = xhtml_template
                else:  # txt format
                    suffix = ".txt"
                    file_content = f"{title}\n\n{content}"

                # Sanitize title for filename
                safe_title_filename = re.sub(r'[\\/*?:"<>|]', "_", title)
                filename = f"{safe_title_filename}{suffix}"
                file_path = bulk_dir / filename

                with file_path.open("w", encoding="utf-8") as f:
                    f.write(file_content)
                self.logger.debug(f"Chapter bulk file saved: {file_path}")
            except Exception as e:
                self.logger.error(
                    f"Failed to save bulk file for chapter {chapter_id}: {e}",
                    exc_info=True,
                )

        # Save status frequently or at the end? For library use, saving at end might be better.
        # self.save_download_status() # Removed frequent saving

    def save_error_chapter(
        self, chapter_id: str, title: Optional[str], error_msg: str = "Error"
    ):
        """Stores error marker for a chapter in memory."""
        safe_title = title if title else f"Chapter {chapter_id}"
        self.downloaded[chapter_id] = [safe_title, error_msg]  # Store error message
        self.logger.debug(
            f"Chapter {chapter_id} download error ('{error_msg}') cached."
        )
        # self.save_download_status() # Removed frequent saving

    def finalize_download(self, chapters: List[Dict], failed_count: int):
        """Saves final status and optionally cleans up. Called after download process."""
        self.save_download_status()  # Save status once at the end

        # Optional: Keep final file generation (like EPUB) if needed
        # If generating files, ensure paths are correct based on config
        output_file = None
        if not self.config.bulk_files and self.config.novel_format == "epub":
            output_file = self.save_dir / f"{self.book_name}.{self.config.novel_format}"
            try:
                if output_file.exists():
                    os.remove(output_file)  # Remove old file

                # Ensure cover image exists before generating EPUB
                cover_path = self.status_folder / f"{self.book_name}.jpg"
                if not cover_path.exists():
                    self.logger.warning(
                        f"Cover image not found at {cover_path}, EPUB will lack cover."
                    )

                # Generate EPUB
                epub = EpubGenerator(
                    identifier=self.book_id,
                    title=self.book_name,
                    language="zh-CN",  # Assuming Chinese
                    author=self.author,
                    description=self.description,
                    publisher="Generated via Backend",
                    # EpubGenerator needs access to config for cover path
                    # It likely gets it via GlobalContext internally
                )

                # Add metadata/description page
                desc_html = f"<h1>简介</h1><p>作者: {self.author}</p><p>标签: {self.tags}</p><p>{self.description}</p>"
                epub.add_chapter("简介", desc_html, "description.xhtml")

                # Add content chapters
                for (
                    chapter_meta
                ) in chapters:  # Iterate through the original chapter list for order
                    chapter_id = chapter_meta["id"]
                    data = self.downloaded.get(chapter_id)
                    if data:
                        ch_title, ch_content = data
                        if (
                            ch_content != "Error"
                            and not isinstance(ch_content, str)
                            or "Error" not in str(ch_content)
                        ):  # Check if content is not an error
                            # Epub requires XHTML content
                            # Assuming ContentParser.clean_for_ebooklib exists and works
                            # Or, generate basic XHTML here
                            # For now, let's assume content is somewhat HTML-ready
                            # A better approach needs robust HTML cleaning/conversion
                            xhtml_chapter_content = f"<h1>{ch_title.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')}</h1><div>{ch_content}</div>"  # Basic structure
                            epub.add_chapter(ch_title, xhtml_chapter_content)
                        else:
                            # Add placeholder for failed chapters
                            epub.add_chapter(
                                ch_title,
                                f"<h1>{ch_title}</h1><p>内容下载失败或未完成 ({ch_content})</p>",
                            )
                    else:
                        # Chapter was expected but not found in downloaded dict
                        epub.add_chapter(
                            chapter_meta.get("title", f"Chapter {chapter_id}"),
                            f"<h1>{chapter_meta.get('title', f'Chapter {chapter_id}')}</h1><p>章节数据丢失</p>",
                        )

                epub.generate(str(output_file))  # Pass path as string
                self.logger.info(f"EPUB generated: {output_file}")

            except Exception as e:
                self.logger.error(
                    f"EPUB generation failed for {self.book_name}: {e}", exc_info=True
                )
                if output_file and output_file.exists():
                    try:
                        os.remove(output_file)  # Clean up failed attempt
                    except OSError:
                        pass
        elif not self.config.bulk_files and self.config.novel_format == "txt":
            output_file = self.save_dir / f"{self.book_name}.txt"
            try:
                with output_file.open("w", encoding="utf-8") as f:
                    f.write(
                        f"书名: {self.book_name}\n作者: {self.author}\n标签: {self.tags}\n简介: {self.description}\n\n"
                    )
                    for chapter_meta in chapters:
                        chapter_id = chapter_meta["id"]
                        data = self.downloaded.get(chapter_id)
                        if data:
                            ch_title, ch_content = data
                            f.write(f"\n\n{ch_title}\n")
                            if (
                                ch_content != "Error"
                                and not isinstance(ch_content, str)
                                or "Error" not in str(ch_content)
                            ):
                                f.write(f"{ch_content}\n")
                            else:
                                f.write(f"[内容下载失败: {ch_content}]\n")
                        else:
                            f.write(
                                f"\n\n{chapter_meta.get('title', f'Chapter {chapter_id}')}\n[章节数据丢失]\n"
                            )
                self.logger.info(f"TXT file generated: {output_file}")
            except Exception as e:
                self.logger.error(
                    f"TXT generation failed for {self.book_name}: {e}", exc_info=True
                )
                if output_file and output_file.exists():
                    try:
                        os.remove(output_file)
                    except OSError:
                        pass

        # Cleanup based on config
        if failed_count == 0 and self.config.auto_clear_dump and self.end:
            self.clear_status_files()

    def clear_status_files(self):
        """Cleans up status file and potentially the cover image."""
        cover_path = self.status_folder / f"{self.book_name}.jpg"
        try:
            if self.status_file.exists():
                os.remove(self.status_file)
                self.logger.debug(f"断点缓存文件已清理！{self.status_file}")
            if cover_path.exists():
                os.remove(cover_path)
                self.logger.debug(f"封面文件已清理！{cover_path}")
            # Optionally clean the whole status folder if empty
            # Be cautious with this if other things might be stored there
            # if self.status_folder.exists() and not any(self.status_folder.iterdir()):
            #     FileCleaner.clean_dump_folder(self.status_folder)
            #     self.logger.debug(f"状态文件夹已清理！{self.status_folder}")
        except Exception as e:
            self.logger.warning(f"清理状态文件时出错: {e}")

    def save_download_status(self):
        """保存完整下载状态到 JSON 文件 (No changes needed conceptually)"""
        if not self.downloaded:
            self.logger.debug("No downloaded data to save in status file.")
            return

        data = {
            "book_id": self.book_id,  # Add book_id for easier identification
            "book_name": self.book_name,
            "author": self.author,
            "tags": self.tags,
            "description": self.description,
            "downloaded": self.downloaded,
        }
        try:
            # Ensure directory exists
            self.status_folder.mkdir(parents=True, exist_ok=True)
            with self.status_file.open("w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            self.logger.debug(f"Download status saved to {self.status_file}")
        except Exception as e:
            self.logger.warning(f"状态文件保存失败: {e}", exc_info=True)

    def get_downloaded_data(self) -> Dict[str, List[Any]]:
        """Returns the dictionary containing downloaded chapter data."""
        return self.downloaded
