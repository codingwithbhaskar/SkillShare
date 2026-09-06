<#
============================================================================
SkillShare  -  query-optimization benchmark harness driver.

Stands up a SEPARATE database (skillshare_benchmark, does not touch
skillshare_dev), applies the same schema/functions/triggers migrations the
real app uses, then for each worker-count scale point:
  1. regenerates synthetic data at exactly that scale
  2. runs the two benchmark queries WITH the relevant indexes
  3. drops those indexes, runs the same two queries again (WITHOUT)
  4. recreates the indexes before moving to the next scale

Every EXPLAIN (ANALYZE, FORMAT JSON) plan is saved as its own .json file
under results\, named <query>_<scale>_<indexed|noindex>.json. Nothing is
printed/interpreted here  -  a separate step (parse_results.py) reads all of
these afterward and builds the comparison table.

REQUIREMENTS
  - psql on PATH (part of the PostgreSQL install you already have  -  check
    with `psql --version`; if not found, add
    "C:\Program Files\PostgreSQL\16\bin" to PATH, or edit $Psql below).
  - $env:SKILLSHARE_DB_PASSWORD set, same as when running the backend
    (see application-dev.yml's comment).

USAGE
  cd D:\Dev\SkillShare\benchmark
  # Quick smoke test first (~seconds):
  .\run_benchmark.ps1 -Scales 1000
  # Full run (~several minutes, mostly the 50k/100k passes):
  .\run_benchmark.ps1
  # Start completely clean (drops skillshare_benchmark if it exists):
  .\run_benchmark.ps1 -Fresh
============================================================================
#>

param(
    [int[]]$Scales = @(1000, 5000, 10000, 50000, 100000),
    [switch]$Fresh,
    [string]$PgHost = "localhost",
    [int]$PgPort = 5433,
    [string]$PgUser = "postgres",
    [string]$DbName = "skillshare_benchmark",
    [string]$PsqlPath = ""
)

$ErrorActionPreference = "Stop"

# Resolve which psql.exe to use: explicit -PsqlPath wins; otherwise try PATH;
# otherwise fall back to the known PostgreSQL 16 install (this project
# targets 16 specifically - see application-dev.yml's comment on why 16/5433
# was chosen over 18/5432; a bare "psql" on this machine could resolve to
# either version since both are installed).
if ($PsqlPath) {
    $Psql = $PsqlPath
} elseif (Get-Command psql -ErrorAction SilentlyContinue) {
    $Psql = "psql"
} elseif (Test-Path "C:\Program Files\PostgreSQL\16\bin\psql.exe") {
    $Psql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
} else {
    Write-Error "Could not find psql.exe. Pass it explicitly: -PsqlPath 'C:\Program Files\PostgreSQL\16\bin\psql.exe'"
    exit 1
}
Write-Host "Using psql: $Psql"

if (-not $env:SKILLSHARE_DB_PASSWORD) {
    Write-Error "SKILLSHARE_DB_PASSWORD is not set. Set it the same way you do for the backend, then re-run."
    exit 1
}
$env:PGPASSWORD = $env:SKILLSHARE_DB_PASSWORD

$BenchDir   = $PSScriptRoot
$SqlDir     = Join-Path $BenchDir "sql"
$ResultsDir = Join-Path $BenchDir "results"
$RepoRoot   = Split-Path $BenchDir -Parent
$MigDir     = Join-Path $RepoRoot "backend\skillshare-backend\src\main\resources\db\migration"
$LogFile    = Join-Path $BenchDir ("run_log_{0}.txt" -f (Get-Date -Format "yyyyMMdd_HHmmss"))

New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null

function Log($msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $msg
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

function Run-Psql {
    param([string]$Database, [string[]]$PsqlArgs, [string]$OutFile = $null)
    $baseArgs = @("-h", $PgHost, "-p", $PgPort, "-U", $PgUser, "-d", $Database, "-v", "ON_ERROR_STOP=1")
    $allArgs = $baseArgs + $PsqlArgs
    if ($OutFile) {
        # Postgres sends NOTICE/WARNING (harmless, e.g. "truncate cascades to
        # table ...") to stderr. When stderr is redirected with *>, PowerShell
        # wraps each line as a NativeCommandError - which throws immediately
        # under $ErrorActionPreference = "Stop", even though nothing actually
        # failed. Relax it for just this one redirected call; $LASTEXITCODE
        # below is still the real signal of success/failure.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & $Psql @allArgs *> $OutFile
        } finally {
            $ErrorActionPreference = $prevEap
        }
    } else {
        & $Psql @allArgs
    }
    if ($LASTEXITCODE -ne 0) {
        Log "FAILED: psql $($PsqlArgs -join ' ')  (exit $LASTEXITCODE)  -  see $LogFile / $OutFile"
        throw "psql step failed"
    }
}

Log "=== SkillShare benchmark harness starting ==="
Log "Scales: $($Scales -join ', ')   Fresh: $Fresh   DB: $DbName@$PgHost`:$PgPort"

# ---------------------------------------------------------------------------
# 1. Database
# ---------------------------------------------------------------------------
if ($Fresh) {
    Log "Dropping existing $DbName (if any)..."
    & $Psql -h $PgHost -p $PgPort -U $PgUser -d postgres -v ON_ERROR_STOP=1 -c `
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DbName' AND pid <> pg_backend_pid();" | Out-Null
    & $Psql -h $PgHost -p $PgPort -U $PgUser -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $DbName;" | Out-Null
}

$exists = & $Psql -h $PgHost -p $PgPort -U $PgUser -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='$DbName';"
if ($exists -ne "1") {
    Log "Creating database $DbName..."
    & $Psql -h $PgHost -p $PgPort -U $PgUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $DbName;"
    if ($LASTEXITCODE -ne 0) { throw "CREATE DATABASE failed" }

    Log "Applying schema migrations (V1, V2, V3, V4, V6  -  V5's demo seed is intentionally skipped)..."
    foreach ($mig in @("V1__schema.sql", "V2__triggers.sql", "V3__views.sql", "V4__functions_procedures.sql", "V6__payment_gateway.sql")) {
        $path = Join-Path $MigDir $mig
        Log "  -> $mig"
        Run-Psql -Database $DbName -PsqlArgs @("-f", $path)
    }

    Log "Applying reference seed (skills, services, allocation_criteria)..."
    Run-Psql -Database $DbName -PsqlArgs @("-f", (Join-Path $SqlDir "00_reference_seed.sql"))
} else {
    Log "$DbName already exists  -  reusing it (pass -Fresh to rebuild from scratch)."
}

# ---------------------------------------------------------------------------
# 2. Per-scale benchmark loop
# ---------------------------------------------------------------------------
foreach ($scale in $Scales) {
    Log "--- Scale $scale workers ---"

    Log "Generating synthetic data..."
    Run-Psql -Database $DbName -PsqlArgs @("-v", "scale=$scale", "-f", (Join-Path $SqlDir "01_generate_synthetic_data.sql")) `
        -OutFile (Join-Path $ResultsDir "generate_$scale.log")

    foreach ($mode in @("indexed", "noindex")) {
        if ($mode -eq "noindex") {
            Log "  Dropping benchmark indexes..."
            Run-Psql -Database $DbName -PsqlArgs @("-f", (Join-Path $SqlDir "04_drop_benchmark_indexes.sql"))
        } else {
            Log "  Ensuring benchmark indexes exist..."
            Run-Psql -Database $DbName -PsqlArgs @("-f", (Join-Path $SqlDir "05_recreate_benchmark_indexes.sql"))
        }

        Log "  Running candidate_search ($mode)..."
        Run-Psql -Database $DbName -PsqlArgs @("-t", "-A", "-f", (Join-Path $SqlDir "02_query_candidate_search.sql")) `
            -OutFile (Join-Path $ResultsDir "candidate_search_${scale}_${mode}.json")

        Log "  Running nearby_workers ($mode)..."
        Run-Psql -Database $DbName -PsqlArgs @("-t", "-A", "-f", (Join-Path $SqlDir "03_query_nearby_workers.sql")) `
            -OutFile (Join-Path $ResultsDir "nearby_workers_${scale}_${mode}.json")
    }

    # Leave the DB in its normal (indexed) state before moving to the next scale.
    Run-Psql -Database $DbName -PsqlArgs @("-f", (Join-Path $SqlDir "05_recreate_benchmark_indexes.sql"))
}

Log "=== Done. Results in $ResultsDir  -  run parse_results.py next. ==="
