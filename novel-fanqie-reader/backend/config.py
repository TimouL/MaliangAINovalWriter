# backend/config.py
import os
from dotenv import load_dotenv

load_dotenv()  # Load .env file
import pymysql

pymysql.install_as_MySQLdb()


class Settings:
    def __init__(self):
        # --- Database Settings ---
        self.DB_TYPE = os.getenv("DB_TYPE", "sqlite").lower()
        default_sqlite_path = os.path.join(os.path.dirname(__file__), "data", "fanqie.db")
        self.SQLITE_DB_FILE = os.getenv("SQLITE_DB_FILE", default_sqlite_path)

        if self.DB_TYPE == "mysql":
            self.SQLALCHEMY_DATABASE_URI = (
                f"mysql+pymysql://{os.getenv('DB_USER', '')}:{os.getenv('DB_PASSWORD', '')}"
                f"@{os.getenv('DB_HOST', '')}:{os.getenv('DB_PORT', '')}/{os.getenv('DB_NAME', '')}?charset=utf8mb4"
            )
        else:
            sqlite_dir = os.path.dirname(self.SQLITE_DB_FILE)
            if sqlite_dir:
                os.makedirs(sqlite_dir, exist_ok=True)
            self.SQLALCHEMY_DATABASE_URI = f"sqlite:///{self.SQLITE_DB_FILE}"

        self.SQLALCHEMY_TRACK_MODIFICATIONS = False
        self.SQLALCHEMY_ECHO = (
            os.getenv("SQLALCHEMY_ECHO", "False").lower() == "true"
        )  # For debugging DB queries

        # --- JWT Settings ---
        self.JWT_SECRET_KEY = os.getenv(
            "JWT_SECRET_KEY", "a-very-secret-key-please-change"
        )
        self.JWT_ACCESS_TOKEN_EXPIRES = int(os.getenv("JWT_ACCESS_TOKEN_EXPIRES_MINUTES", 60)) * 60

        # --- Celery Settings ---
        self.CELERY_BROKER_URL = os.getenv("CELERY_BROKER_URL", "redis://127.0.0.1:6379/0")
        self.CELERY_RESULT_BACKEND = os.getenv("CELERY_RESULT_BACKEND", "redis://127.0.0.1:6379/1")
        self.CELERY_TASK_SERIALIZER = "json"
        self.CELERY_RESULT_SERIALIZER = "json"
        self.CELERY_ACCEPT_CONTENT = ["json"]
        self.CELERY_TIMEZONE = "Asia/Shanghai"
        self.CELERY_ENABLE_UTC = True
        self.CELERY_BROKER_CONNECTION_RETRY_ON_STARTUP = True

        # --- File Storage Paths ---
        self.DATA_BASE_PATH = os.getenv(
            "DATA_BASE_PATH", os.path.join(os.path.dirname(__file__), "data")
        )

        # Word Cloud Image Save Path
        self.WORDCLOUD_SAVE_SUBDIR = "wordclouds"
        self.WORDCLOUD_SAVE_PATH = os.path.join(self.DATA_BASE_PATH, self.WORDCLOUD_SAVE_SUBDIR)

        # --- novel_downloader Configuration Mapping ---
        self.NOVEL_SAVE_PATH = os.getenv(
            "NOVEL_SAVE_PATH", os.path.join(self.DATA_BASE_PATH, "novel_downloads")
        )
        self.NOVEL_STATUS_PATH = os.getenv(
            "NOVEL_STATUS_PATH", os.path.join(self.DATA_BASE_PATH, "novel_status")
        )

        self.NOVEL_MAX_WORKERS = int(os.getenv("NOVEL_MAX_WORKERS", 5))
        self.NOVEL_REQUEST_TIMEOUT = int(os.getenv("NOVEL_REQUEST_TIMEOUT", 20))
        self.NOVEL_MAX_RETRIES = int(os.getenv("NOVEL_MAX_RETRIES", 3))
        self.NOVEL_MIN_WAIT_TIME = int(os.getenv("NOVEL_MIN_WAIT_TIME", 800))
        self.NOVEL_MAX_WAIT_TIME = int(os.getenv("NOVEL_MAX_WAIT_TIME", 1500))
        self.NOVEL_MIN_CONNECT_TIMEOUT = float(os.getenv("NOVEL_MIN_CONNECT_TIMEOUT", 3.1))
        self.NOVEL_NOVEL_FORMAT = os.getenv("NOVEL_FORMAT", "epub").lower()
        self.NOVEL_BULK_FILES = os.getenv("NOVEL_BULK_FILES", "False").lower() == "true"
        self.NOVEL_AUTO_CLEAR_DUMP = os.getenv("NOVEL_AUTO_CLEAR", "True").lower() == "true"
        self.NOVEL_USE_PROXY_API = os.getenv("NOVEL_USE_PROXY_API", "True").lower() == "true"
        self.NOVEL_USE_OFFICIAL_API = os.getenv("NOVEL_USE_OFFICIAL_API", "False").lower() == "true"
        self.NOVEL_IID = os.getenv("NOVEL_IID", "")
        self.NOVEL_IID_SPAWN_TIME = os.getenv("NOVEL_IID_SPAWN_TIME", "")

        self.NOVEL_API_ENDPOINTS_STR = os.getenv("NOVEL_API_ENDPOINTS", "")
        self.NOVEL_API_ENDPOINTS = [
            url.strip() for url in self.NOVEL_API_ENDPOINTS_STR.split(",") if url.strip()
        ]

        # --- General App Settings ---
        self.DEBUG = os.getenv("FLASK_ENV", "production") == "development"
        self.SECRET_KEY = os.getenv("FLASK_SECRET_KEY", "another-secret-key-please-change")

        self._ensure_dir(self.DATA_BASE_PATH)
        self._ensure_dir(self.WORDCLOUD_SAVE_PATH)
        self._ensure_dir(self.NOVEL_SAVE_PATH)
        self._ensure_dir(self.NOVEL_STATUS_PATH)

    @staticmethod
    def _ensure_dir(path):
        if path:
            os.makedirs(path, exist_ok=True)


settings = Settings()


# Helper function to get downloader config as dict
def get_downloader_config():
    return {
        "save_path": settings.NOVEL_SAVE_PATH,
        "status_folder_path_base": settings.NOVEL_STATUS_PATH,
        "max_workers": settings.NOVEL_MAX_WORKERS,
        "request_timeout": settings.NOVEL_REQUEST_TIMEOUT,
        "max_retries": settings.NOVEL_MAX_RETRIES,
        "max_wait_time": settings.NOVEL_MAX_WAIT_TIME,
        "min_wait_time": settings.NOVEL_MIN_WAIT_TIME,
        "min_connect_timeout": settings.NOVEL_MIN_CONNECT_TIMEOUT,
        "novel_format": settings.NOVEL_NOVEL_FORMAT,
        "bulk_files": settings.NOVEL_BULK_FILES,
        "auto_clear_dump": settings.NOVEL_AUTO_CLEAR_DUMP,
        "use_proxy_api": settings.NOVEL_USE_PROXY_API,  # 代理模式
        "use_official_api": settings.NOVEL_USE_OFFICIAL_API,
        "api_endpoints": settings.NOVEL_API_ENDPOINTS,
        "iid": settings.NOVEL_IID,  # Pass these through
        "iid_spawn_time": settings.NOVEL_IID_SPAWN_TIME,
        # Add other fields from novel_downloader's Config if needed
    }
