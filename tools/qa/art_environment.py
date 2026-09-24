"""Detect crashes whose faulting native frame is in ART, without clearing logs."""
import re


def art_crashes(text):
    incidents = []
    # Accept both raw tombstones and timestamped `adb logcat -b crash` output.
    for block in re.split(r'\*\*\* \*\*\* \*\*\*', text):
        frame = re.search(r'#00\s+pc\s+[0-9a-fA-F]+\s+[^\r\n]*\blibart\.so\b[^\r\n]*', block)
        if not frame:
            continue
        process = re.search(r'Cmdline:\s*([^\r\n]+)', block)
        if process is None:
            process = re.search(r'>>>\s*(.*?)\s*<<<', block)
        signal = re.search(r'signal \d+[^\r\n]*', block)
        incidents.append(dict(process=process[1].strip() if process else 'unknown',
                              faulting_frame=frame[0], signal=signal[0] if signal else None))
    return incidents
