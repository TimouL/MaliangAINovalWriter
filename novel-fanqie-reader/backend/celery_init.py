# backend/celery_init.py
from celery import Celery, Task  # Import Task
import os
from dotenv import load_dotenv
import logging  # Import logging

load_dotenv()

# 创建 Celery 实例但不立即关联 Flask 应用
celery_app = Celery(
    "tasks",
    broker=os.getenv("CELERY_BROKER_URL", "redis://127.0.0.1:6379/0"),
    backend=os.getenv("CELERY_RESULT_BACKEND", "redis://127.0.0.1:6379/1"),
    # Include the tasks module so Celery discovers the tasks
    include=["tasks"],
)

celery_app.conf.update(
    broker_connection_retry_on_startup=True,  # Good practice for resilience
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone=os.getenv("TZ", "UTC"),  # Use TZ environment variable or default to UTC
    enable_utc=True,
)


# --- 修改 ContextTask 类 ---
class ContextTask(Task):
    abstract = True

    def __call__(self, *args, **kwargs):
        # 在方法内部导入 Flask 应用，而不是在模块级别
        try:
            # 导入 Flask 当前应用代理对象
            from flask import current_app

            # 尝试使用当前应用上下文，如果在 Flask 应用中运行任务则可用
            if current_app and current_app._get_current_object():
                flask_app = current_app._get_current_object()
            else:
                # 如果在 Celery worker 中运行，需要显式导入应用
                try:
                    # 仅当在 Celery worker 中运行时尝试此导入
                    import app

                    flask_app = app.app
                except ImportError as import_err:
                    task_logger = logging.getLogger(f"celery.task.{self.name}")
                    task_logger.error(
                        f"Task {self.request.id}: Could not import Flask app: {import_err}",
                        exc_info=True,
                    )
                    raise RuntimeError(
                        "Flask app could not be imported. Make sure app.py is properly accessible."
                    )
        except Exception as e:
            task_logger = logging.getLogger(f"celery.task.{self.name}")
            task_logger.error(
                f"Task {self.request.id}: Failed to obtain Flask app: {e}",
                exc_info=True,
            )
            raise RuntimeError(
                "Flask app context could not be established for the task"
            )

        # 在 Flask 应用上下文中执行任务
        with flask_app.app_context():
            # 在上下文中导入数据库实例
            from database import db

            task_logger = logging.getLogger(f"celery.task.{self.name}")
            try:
                task_logger.debug(
                    f"Task {self.request.id}: Entering Flask app context."
                )
                # 执行原始任务的 run 方法
                result = self.run(*args, **kwargs)
                task_logger.debug(
                    f"Task {self.request.id}: Exiting Flask app context normally."
                )
                return result
            except Exception as e:
                # 记录任务执行过程中发生的异常
                task_logger.error(
                    f"Exception in task {self.request.id}: {e}", exc_info=True
                )
                # 发生错误时尝试回滚数据库会话
                try:
                    task_logger.warning(
                        f"Task {self.request.id}: Rolling back DB session due to exception."
                    )
                    db.session.rollback()
                except Exception as db_rollback_err:
                    task_logger.error(
                        f"Task {self.request.id}: Error rolling back DB session: {db_rollback_err}"
                    )
                raise  # 重新抛出异常，使 Celery 将任务标记为 FAILED
            finally:
                # 确保任务执行后删除数据库会话（无论成功或失败）
                # 这可以防止任务之间的会话泄漏
                try:
                    task_logger.debug(f"Task {self.request.id}: Removing DB session.")
                    db.session.remove()
                except Exception as db_remove_err:
                    task_logger.error(
                        f"Task {self.request.id}: Error removing DB session: {db_remove_err}"
                    )


# --- 设置 celery_app 的基本任务类 ---
# 使用 @celery_app.task 定义的所有任务都将继承自 ContextTask
celery_app.Task = ContextTask


# 函数用于在 Flask 应用创建后配置 Celery
def configure_celery(app):
    """配置 Celery 应用，使其使用 Flask 应用的配置。在 Flask 应用创建后调用。"""
    # 使用 Flask 应用配置更新 Celery 配置
    celery_app.conf.update(
        broker_url=app.config["CELERY_BROKER_URL"],
        result_backend=app.config["CELERY_RESULT_BACKEND"],
        # 可以在此处添加其他 Celery 配置覆盖
    )

    # 在此处记录配置信息是安全的，因为 Flask 应用已经创建
    app.logger.info(
        f"Celery linked with Broker: {celery_app.conf.broker_url}, Backend: {celery_app.conf.result_backend}"
    )

    return celery_app
