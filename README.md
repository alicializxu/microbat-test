# Microbat Debugger
A feedback-based debugger for interactively recommending suspicious step in buggy program execution.

![Snapshot of Microbat](/microbat/image/microbat_snapshot.jpg?raw=true "Snapshot of Microbat")

Microbat is a feedback-based debugger which aims to locate bug by interactively recommending suspicious program steps with developers' feedback. Given a buggy program, Microbat records its execution trace and allow developers to make light-weight feedback on trace steps, such as correct-step, wrong-variable-value, wrong-path, and unclear. Microbat reasons and analyzes the feedback along with program information to recommend a suspicious step for further inspection and feedback. Such a debugging process continues until the bug is found. A short demonstration of Microbat is available in https://www.youtube.com/watch?v=jA3131MWuzs.
