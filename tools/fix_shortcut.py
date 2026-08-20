r"""更新桌面快捷方式的目标路径。

用法（Git Bash）：
    cd D:/TimeSeriesForecast
    /d/RAG/rag_proj_env/Scripts/python.exe tools/fix_shortcut.py

说明：
- 桌面真实路径被 Windows 重定向到 OneDrive：HKCU\...\Explorer\User Shell Folders\Desktop
- PowerShell 的 WScript.Shell COM 在沙箱会被拦截；改用 pywin32 的 ShellLink COM
- pywin32 装在 D:/RAG/rag_proj_env（系统 python 没装），用它的解释器
"""

import os
import sys

import pythoncom
from win32com.shell import shell


# 桌面 .lnk 路径（OneDrive 真实路径，参考用户 MEMORY.md）
DESKTOP_LNK = r"C:\Users\xuanx\OneDrive\桌面\智能时序预测平台.lnk"

# 新的启动脚本
TARGET = r"D:\TimeSeriesForecast\start.bat"
WORK_DIR = r"D:\TimeSeriesForecast"
DESCRIPTION = "智能时序预测平台 (Python 版 / FastAPI :8000)"
# 用 Python 解释器图标，更友好
ICON_PATH = r"C:\Users\xuanx\.workbuddy\binaries\python\versions\3.13.12\python.exe"
ICON_INDEX = 0


def make_shortcut(lnk_path: str, target: str, work_dir: str, description: str,
                  icon_path: str = "", icon_index: int = 0) -> None:
    pythoncom.CoInitialize()
    try:
        sh = pythoncom.CoCreateInstance(
            shell.CLSID_ShellLink,
            None,
            pythoncom.CLSCTX_INPROC_SERVER,
            shell.IID_IShellLinkW,
        )
        sh.SetPath(target)
        sh.SetWorkingDirectory(work_dir)
        sh.SetDescription(description)
        if icon_path:
            sh.SetIconLocation(icon_path, icon_index)
        # 通过 IPersistFile 保存
        pf = sh.QueryInterface(pythoncom.IID_IPersistFile)
        pf.Save(lnk_path, 0)
    finally:
        pythoncom.CoUninitialize()


def read_shortcut(lnk_path: str) -> dict:
    pythoncom.CoInitialize()
    try:
        sh = pythoncom.CoCreateInstance(
            shell.CLSID_ShellLink,
            None,
            pythoncom.CLSCTX_INPROC_SERVER,
            shell.IID_IShellLinkW,
        )
        pf = sh.QueryInterface(pythoncom.IID_IPersistFile)
        pf.Load(lnk_path, 0)
        return {
            "path": sh.GetPath(shell.SLGP_UNCPRIORITY)[0],
            "work_dir": sh.GetWorkingDirectory(),
            "description": sh.GetDescription(),
            "icon": sh.GetIconLocation()[0] if sh.GetIconLocation()[0] else "(default)",
        }
    finally:
        pythoncom.CoUninitialize()


def main() -> int:
    if not os.path.exists(DESKTOP_LNK):
        print(f"[新创建] 快捷方式不存在: {DESKTOP_LNK}")
    else:
        print("[更新前]")
        for k, v in read_shortcut(DESKTOP_LNK).items():
            print(f"  {k}: {v}")
        print()

    make_shortcut(DESKTOP_LNK, TARGET, WORK_DIR, DESCRIPTION, ICON_PATH, ICON_INDEX)
    print(f"[已写入] {DESKTOP_LNK}")
    print("[更新后]")
    for k, v in read_shortcut(DESKTOP_LNK).items():
        print(f"  {k}: {v}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
