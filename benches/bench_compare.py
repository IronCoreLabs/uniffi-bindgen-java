#!/usr/bin/env python3
"""Parse Criterion benchmark output files and produce comparison tables.

Usage:
    python3 bench_compare.py "Column Title" path/to/bench.txt ["Title2" path2.txt ...]
    python3 bench_compare.py --speedup "Faster" file1.txt "Slower" file2.txt
    nix shell nixpkgs#python3 -c python3 bench_compare.py ...

The language prefix (e.g. "java-") is extracted from bench names and appended
to the column title automatically.

With --speedup, exactly two columns are expected and the output is a speedup
table showing how much faster the first column is vs the second (ratio > 1
means first is faster, < 1 means second is faster).
"""

import re
import sys
from collections import OrderedDict

UNIT_TO_NS = {'ns': 1, 'µs': 1e3, 'us': 1e3, 'ms': 1e6, 's': 1e9}


def parse_bench_file(path):
    """Parse a Criterion benchmark output file.

    Returns (language, results) where results maps
    (category, bench_name) -> nanoseconds as float.
    """
    results = {}
    current_bench = None
    language = None

    name_re = re.compile(r'^(\S+?)/(\w+?)-(\S+?)(?:\s+time:|\s*$)')
    time_re = re.compile(r'time:\s+\[[\d.]+ \S+ ([\d.]+) (ns|µs|us|ms|s) [\d.]+ \S+\]')

    with open(path) as f:
        for line in f:
            stripped = line.strip()

            name_m = name_re.match(stripped)
            if name_m:
                language = language or name_m.group(2)
                current_bench = (name_m.group(1), name_m.group(3))

            time_m = time_re.search(line)
            if time_m and current_bench:
                ns = float(time_m.group(1)) * UNIT_TO_NS[time_m.group(2)]
                results[current_bench] = ns
                current_bench = None

    return language, results


def format_time(ns):
    if ns < 1000:
        return f"{ns:.2f} ns" if ns >= 10 else f"{ns:.1f} ns"
    elif ns < 1e6:
        return f"{ns / 1e3:.2f} us"
    elif ns < 1e9:
        return f"{ns / 1e6:.2f} ms"
    else:
        return f"{ns / 1e9:.2f} s"


def format_speedup(ratio):
    if ratio >= 10:
        return f"{ratio:.0f}x"
    else:
        return f"{ratio:.1f}x"


def collect_categories(all_results):
    categories = OrderedDict()
    for results in all_results:
        for cat, bench in results:
            if cat not in categories:
                categories[cat] = OrderedDict()
            if bench not in categories[cat]:
                categories[cat][bench] = True
    return categories


def print_comparison(columns, all_results):
    categories = collect_categories(all_results)

    for cat, benches in categories.items():
        print(f"### {cat}\n")
        header = "Test Case | " + " | ".join(columns)
        sep = "-- | " + " | ".join("--" for _ in columns)
        print(header)
        print(sep)

        for bench in benches:
            values = []
            for results in all_results:
                ns = results.get((cat, bench))
                values.append(format_time(ns) if ns is not None else "")
            print(f"{bench} | " + " | ".join(values))

        print()


def print_speedup(columns, all_results):
    categories = collect_categories(all_results)
    a_results, b_results = all_results
    title = f"{columns[0]} vs {columns[1]} Speedup"

    cat_names = list(categories.keys())

    print(f"### {title}\n")
    header = "Test Case | " + " | ".join(f"{cat} Speedup" for cat in cat_names)
    sep = "-- | " + " | ".join("--" for _ in cat_names)
    print(header)
    print(sep)

    all_benches = OrderedDict()
    for benches in categories.values():
        for b in benches:
            all_benches[b] = True

    for bench in all_benches:
        values = []
        for cat in cat_names:
            a_ns = a_results.get((cat, bench))
            b_ns = b_results.get((cat, bench))
            if a_ns is not None and b_ns is not None and a_ns > 0:
                values.append(format_speedup(b_ns / a_ns))
            else:
                values.append("")
        print(f"{bench} | " + " | ".join(values))

    print()


def main():
    args = sys.argv[1:]

    speedup = False
    if "--speedup" in args:
        speedup = True
        args.remove("--speedup")

    if len(args) < 2 or len(args) % 2 != 0:
        print("Usage: bench_compare.py [--speedup] \"Title1\" file1.txt [\"Title2\" file2.txt ...]",
              file=sys.stderr)
        sys.exit(1)

    if speedup and len(args) != 4:
        print("--speedup requires exactly two title/file pairs", file=sys.stderr)
        sys.exit(1)

    columns = []
    all_results = []
    for i in range(0, len(args), 2):
        title = args[i]
        path = args[i + 1]
        lang, results = parse_bench_file(path)
        if lang:
            title = f"{title} ({lang.capitalize()})"
        columns.append(title)
        all_results.append(results)

    if speedup:
        print_speedup(columns, all_results)
    else:
        print_comparison(columns, all_results)


if __name__ == "__main__":
    main()
