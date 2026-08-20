"""更新桌面快捷方式为目标 start.bat（cmd /c 风格，与资源管理器创建的 cmd 快捷方式一致）。

用法（Git Bash）：
    cd D:/TimeSeriesForecast
    /d/RAG/rag_proj_env/Scripts/python.exe tools/fix_shortcut.py

说明：
- pywin32 用 SetPath 写 .bat 时，生成的 .lnk 缺少完整的 link target ID list，
  Windows shell 双击时偶发 "系统找不到指定的路径"（ID list 解析失败）
- 解决方法：把 .lnk 设成 cmd.exe + /c "start.bat" 风格，与 explorer 标准格式一致
- pywin32 装在 D:/RAG/rag_proj_env（系统 python 没装），必须用这个解释器
"""

import os
import sys

import pythoncom
from win32com.shell import shell


# 桌面 .lnk 路径（OneDrive 真实路径，参考用户 MEMORY.md）
DESKTOP_LNK = r"C:\Users\xuanx\OneDrive\桌面\智能时序预测平台.lnk"

# 启动方式：cmd.exe /c 跑 start.bat（与 explorer 创建的 cmd 快捷方式一致）
CMD_EXE = r"C:\Windows\System32\cmd.exe"
ARGS = r'/c "D:\TimeSeriesForecast\start.bat"'
WORK_DIR = r"D:\TimeSeriesForecast"
DESCRIPTION = "智能时序预测平台 (Python 版 / FastAPI :8000)"
ICON_PATH = r"C:\Users\xuanx\.workbuddy\binaries\python\versions\3.13.12\python.exe"
ICON_INDEX = 0
# SW_SHOWNORMAL = 1
SHOW_CMD = 1


def make_shortcut(lnk_path: str, target: str, arguments: str, work_dir: str,
                  description: str, icon_path: str, icon_index: int, show_cmd: int) -> None:
    pythoncom.CoInitialize()
    try:
        sh = pythoncom.CoCreateInstance(
            shell.CLSID_ShellLink,
            None,
            pythoncom.CLSCTX_INPROC_SERVER,
            shell.IID_IShellLinkW,
        )
        sh.SetPath(target)
        sh.SetArguments(arguments)
        sh.SetWorkingDirectory(work_dir)
        sh.SetDescription(description)
        sh.SetShowCmd(show_cmd)
        if icon_path:
            sh.SetIconLocation(icon_path, icon_index)
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
            "arguments": sh.GetArguments(),
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

    make_shortcut(DESKTOP_LNK, CMD_EXE, ARGS, WORK_DIR, DESCRIPTION, ICON_PATH, ICON_INDEX, SHOW_CMD)
    print(f"[已写入] {DESKTOP_LNK}")
    print("[更新后]")
    for k, v in read_shortcut(DESKTOP_LNK).items():
        print(f"  {k}: {v}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
