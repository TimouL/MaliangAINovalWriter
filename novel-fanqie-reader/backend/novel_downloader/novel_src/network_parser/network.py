# backend/novel_downloader/novel_src/network_parser/network.py
# -------------------------------
# network.py - 网络请求模块
# 职责：处理所有HTTP请求相关逻辑
# -------------------------------
import json
import requests
from typing import Optional, Dict, List
from fake_useragent import UserAgent

from ..base_system.context import GlobalContext
from ..book_parser.parser import ContentParser

# Assuming search_api comes from here, adjust if necessary
from ..offical_tools.downloader import search_api


class NetworkClient:
    """网络请求客户端"""

    def __init__(self):
        self.logger = GlobalContext.get_logger()
        self.config = GlobalContext.get_config()
        self._api_status: Dict[str, dict] = {}  # API状态跟踪字典
        self._init_api_status()

    def _init_api_status(self):
        """初始化API状态跟踪器"""
        for endpoint in self.config.api_endpoints:
            self._api_status[endpoint] = {
                "failure_count": 0,
                "last_success": 0.0,
                "response_time": float("inf"),
            }

    def get_headers(self, cookie: Optional[str] = None) -> Dict[str, str]:
        """生成随机请求头

        Args:
            cookie: 可选Cookie字符串

        Returns:
            包含随机User-Agent的请求头字典
        """
        ua = UserAgent(
            browsers=["Chrome", "Edge"],  # 限定主流浏览器
            os=["Windows"],  # 仅Windows系统
            platforms=["desktop"],  # 仅桌面端
            fallback="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0",  # 备用UA
        )
        headers = {
            "User-Agent": ua.random,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cache-Control": "max-age=0",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
        }
        self.logger.debug(f"Header: {headers}")
        if cookie:
            headers["Cookie"] = cookie
        return headers

    # <<< MODIFIED FUNCTION >>>
    def search_book(self, book_name: str) -> List[Dict]:
        """
        Searches for books using the provided name and returns a list of results.

        Args:
            book_name: The name of the book to search for.

        Returns:
            A list of dictionaries, where each dictionary represents a found book
            (e.g., {'title': '...', 'book_id': '...', 'author': '...'}).
            Returns an empty list if no books are found or an error occurs.
        """
        try:
            self.logger.info(f"Initiating search for book: '{book_name}'")
            # Call the existing search logic which presumably returns a list of dicts
            search_datas = search_api(book_name)  #

            if not search_datas:
                self.logger.warning(f"No search results found for query: '{book_name}'")
                return []

            # Log the results (optional, good for debugging)
            self.logger.info(f"Found {len(search_datas)} results for '{book_name}'.")
            for num, search_res in enumerate(search_datas):
                self.logger.debug(  # Changed to debug level to avoid flooding logs in production
                    f"{num + 1}. 书名: {search_res.get('title', 'N/A')} | "
                    f"ID: {search_res.get('book_id', 'N/A')} | "
                    f"作者: {search_res.get('author', 'N/A')}"
                )

            # --- Removed the input() loop ---

            # Return the list of results directly
            return search_datas

        except Exception as e:
            self.logger.error(
                f"Error during book search for '{book_name}': {e}", exc_info=True
            )
            # Return an empty list in case of any error during the search
            return []

    # <<< END OF MODIFIED FUNCTION >>>

    def get_book_info(
        self, book_id: str
    ) -> Optional[tuple]:  # Changed return type hint for clarity
        """
        Fetches detailed information for a specific book ID.

        Args:
            book_id: The unique ID of the book.

        Returns:
            A tuple containing (book_name, author, description, tags, chapter_count)
            or None if the book is not found or an error occurs.
        """
        book_info_url = f"https://fanqienovel.com/page/{book_id}"
        self.logger.info(f"Fetching book info for ID: {book_id} from {book_info_url}")

        # 发送请求
        try:
            response = requests.get(
                book_info_url,
                headers=self.get_headers(),
                timeout=self.config.request_timeout,  # Use timeout from config
            )
            if response.status_code == 404:
                self.logger.error(
                    f"Book info request failed: Novel ID {book_id} not found (404)."
                )  #
                return None
            response.raise_for_status()  # Raise HTTPError for bad responses (4xx or 5xx)

            self.logger.debug(f"Successfully fetched book info page for ID: {book_id}")
            # Use the ContentParser to extract info
            parsed_info = ContentParser.parse_book_info(response.text, book_id)  #
            if parsed_info:
                self.logger.info(f"Successfully parsed book info for ID: {book_id}")
                return parsed_info
            else:
                # This case might happen if parsing fails even with a 200 response
                self.logger.error(
                    f"Failed to parse book info from page content for ID: {book_id}. Content length: {len(response.text)}"
                )
                return None

        except requests.exceptions.RequestException as e:
            self.logger.error(
                f"Network error fetching book info for ID {book_id}: {e}", exc_info=True
            )
            return None
        except Exception as e:
            # Catch potential errors during parsing within ContentParser.parse_book_info if not handled there
            self.logger.error(
                f"Unexpected error getting book info for ID {book_id}: {e}",
                exc_info=True,
            )
            return None

    def fetch_chapter_list(self, book_id: str) -> Optional[List[Dict]]:
        """从API获取章节列表"""
        api_url = (
            f"https://fanqienovel.com/api/reader/directory/detail?bookId={book_id}"
        )
        try:
            self.logger.debug(f"开始获取章节列表，URL: {api_url}")
            response = requests.get(
                api_url, headers=self.get_headers(), timeout=self.config.request_timeout
            )
            self.logger.debug(
                f"章节列表响应状态: {response.status_code} 长度: {len(response.text)}字节"
            )

            response.raise_for_status()
            # Check if response content type is JSON before parsing
            if "application/json" in response.headers.get("Content-Type", ""):
                return self._parse_chapter_data(response.json())  #
            else:
                self.logger.error(
                    f"Unexpected content type for chapter list API: {response.headers.get('Content-Type')}. Expected JSON."
                )
                self.logger.debug(
                    f"Non-JSON response content preview: {response.text[:200]}..."
                )
                return None

        except requests.exceptions.RequestException as e:
            self.logger.error(
                f"Network error fetching chapter list for book ID {book_id}: {e}",
                exc_info=True,
            )
            return None
        except json.JSONDecodeError as e:
            self.logger.error(
                f"JSON decoding error fetching chapter list for book ID {book_id}: {e}",
                exc_info=True,
            )
            if "response" in locals():
                self.logger.debug(f"Invalid JSON content: {response.text[:200]}...")
            return None
        except Exception as e:
            self.logger.error(f"获取章节列表失败: {str(e)}", exc_info=True)
            if "response" in locals() and hasattr(response, "text"):
                self.logger.debug(f"错误响应内容: {response.text[:200]}...")
            return None

    def _parse_chapter_data(
        self, response_data: dict
    ) -> Optional[List[Dict]]:  # Return Optional in case parsing fails
        """解析章节API响应"""
        try:
            self.logger.debug(f"开始解析章节数据，响应码: {response_data.get('code')}")

            if response_data.get("code") != 0:
                self.logger.error(
                    f"Chapter list API returned error code {response_data.get('code')}: {response_data.get('message')}"
                )
                self.logger.debug(
                    f"API错误数据: {json.dumps(response_data, ensure_ascii=False)[:200]}..."
                )
                # Optionally raise an exception or return None/empty list based on desired handling
                return None  # Return None on API error

            chapters = response_data.get("data", {}).get(
                "allItemIds"
            )  # Safely access nested keys
            if chapters is None:  # Check if the key exists and is not None
                self.logger.error(
                    "Chapter list API response missing 'data.allItemIds'."
                )
                self.logger.debug(
                    f"Response data structure: {json.dumps(response_data, ensure_ascii=False)[:200]}..."
                )
                return None  # Return None if data structure is unexpected

            if not isinstance(chapters, list):
                self.logger.error(
                    f"'data.allItemIds' is not a list, type is {type(chapters)}."
                )
                return None

            self.logger.info(f"解析到{len(chapters)}个章节ID，示例: {chapters[:3]}...")
            # Ensure chapter_id is treated as a string if necessary
            return [
                {"id": str(chapter_id), "title": f"第{idx + 1}章", "index": idx}
                for idx, chapter_id in enumerate(chapters)
            ]
        except Exception as e:
            # Catch any unexpected error during parsing
            self.logger.error(f"Error parsing chapter data: {e}", exc_info=True)
            return None
