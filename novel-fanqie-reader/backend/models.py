# backend/models.py
from datetime import datetime
from werkzeug.security import generate_password_hash, check_password_hash
from database import db
import enum


class TaskStatus(enum.Enum):
    PENDING = "PENDING"
    DOWNLOADING = "DOWNLOADING"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    TERMINATED = "TERMINATED"


class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(32), unique=True, nullable=False)
    password = db.Column(db.String(256), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    last_login_at = db.Column(db.DateTime)

    download_tasks = db.relationship(
        "DownloadTask", backref="user", lazy=True, cascade="all, delete-orphan"
    )

    def set_password(self, raw):
        self.password = generate_password_hash(raw)

    def check_password(self, raw):
        return check_password_hash(self.password, raw)


class Novel(db.Model):
    id = db.Column(db.BigInteger, primary_key=True)
    title = db.Column(db.String(255), nullable=False)
    author = db.Column(db.String(128))
    description = db.Column(db.Text)
    tags = db.Column(db.String(255))
    status = db.Column(db.String(32))
    total_chapters = db.Column(db.Integer)
    cover_image_url = db.Column(db.String(512))
    last_crawled_at = db.Column(db.DateTime)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

    chapters = db.relationship(
        "Chapter", backref="novel", lazy=True, cascade="all, delete-orphan"
    )
    word_stats = db.relationship(
        "WordStat", backref="novel", lazy=True, cascade="all, delete-orphan"
    )
    download_tasks = db.relationship("DownloadTask", backref="novel", lazy=True)


class Chapter(db.Model):
    id = db.Column(db.BigInteger, primary_key=True)
    novel_id = db.Column(db.BigInteger, db.ForeignKey("novel.id"), nullable=False)
    chapter_index = db.Column(db.Integer, nullable=False)
    title = db.Column(db.String(255), nullable=False)
    content = db.Column(db.Text, nullable=False)
    fetched_at = db.Column(db.DateTime, default=datetime.utcnow)


class WordStat(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    novel_id = db.Column(db.BigInteger, db.ForeignKey("novel.id"), nullable=False)
    word = db.Column(db.String(64), nullable=False)
    freq = db.Column(db.Integer, nullable=False)

    __table_args__ = (db.Index("ix_wordstat_novel_word", "novel_id", "word"),)


class DownloadTask(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("user.id"), nullable=False)
    novel_id = db.Column(db.BigInteger, db.ForeignKey("novel.id"), nullable=False)
    celery_task_id = db.Column(db.String(128), unique=True, nullable=True)
    status = db.Column(db.Enum(TaskStatus), default=TaskStatus.PENDING, nullable=False)
    progress = db.Column(db.Integer, default=0)
    message = db.Column(db.String(255), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    updated_at = db.Column(
        db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow
    )

    __table_args__ = (db.Index("ix_downloadtask_user_status", "user_id", "status"),)

    def to_dict(self):
        novel_info = (
            {
                "id": str(self.novel.id),
                "title": self.novel.title,
                "author": self.novel.author,
            }
            if self.novel
            else None
        )

        return {
            "id": self.id,
            "user_id": self.user_id,
            "novel_id": str(self.novel_id),
            "novel": novel_info,
            "celery_task_id": self.celery_task_id,
            "status": self.status.name,
            "progress": self.progress,
            "message": self.message,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
