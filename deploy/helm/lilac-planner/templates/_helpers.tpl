{{/* Base name, overridable. */}}
{{- define "lilac.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Fully-qualified app name (release-scoped). */}}
{{- define "lilac.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "lilac.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/* Common labels. */}}
{{- define "lilac.labels" -}}
app.kubernetes.io/name: {{ include "lilac.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/* Component-scoped resource names. */}}
{{- define "lilac.backend.fullname" -}}{{ include "lilac.fullname" . }}-backend{{- end -}}
{{- define "lilac.frontend.fullname" -}}{{ include "lilac.fullname" . }}-frontend{{- end -}}
{{- define "lilac.db.fullname" -}}{{ include "lilac.fullname" . }}-mariadb{{- end -}}

{{/* Name of the Secret holding DB credentials (existing or chart-created). */}}
{{- define "lilac.db.secretName" -}}
{{- if .Values.db.existingSecret -}}
{{- .Values.db.existingSecret -}}
{{- else -}}
{{- printf "%s-db" (include "lilac.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* Name of the Secret holding native-auth secrets (existing or chart-created). */}}
{{- define "lilac.auth.secretName" -}}
{{- if .Values.auth.native.existingSecret -}}
{{- .Values.auth.native.existingSecret -}}
{{- else -}}
{{- printf "%s-auth" (include "lilac.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* Effective password-reset URL: explicit value, else derived from the ingress host. */}}
{{- define "lilac.auth.resetUrl" -}}
{{- if .Values.auth.native.resetUrl -}}
{{- .Values.auth.native.resetUrl -}}
{{- else if .Values.ingress.host -}}
{{- printf "https://%s/reset-password" .Values.ingress.host -}}
{{- else -}}
{{- "http://localhost:5173/reset-password" -}}
{{- end -}}
{{- end -}}

{{/* Effective image registry prefix (with trailing slash, or empty). */}}
{{- define "lilac.imagePrefix" -}}
{{- if .Values.image.registry -}}
{{- printf "%s/" .Values.image.registry -}}
{{- end -}}
{{- end -}}

{{/*
  Effective backend JDBC URL.
  - embedded mode: derive from the in-cluster MariaDB service (embedded mode
    is MariaDB-only by design - see mariadb-statefulset.yaml).
  - external mode: use the user-supplied db.url.
*/}}
{{- define "lilac.db.url" -}}
{{- if eq .Values.db.mode "embedded" -}}
{{- printf "jdbc:mariadb://%s:3306/%s" (include "lilac.db.fullname" .) .Values.db.name -}}
{{- else -}}
{{- required "db.url is required when db.mode=external" .Values.db.url -}}
{{- end -}}
{{- end -}}

{{/*
  DB credential env var / Secret key names, keyed by backend.plannerDb - the
  backend reads different env vars per storage profile (MARIADB_* for
  mariadb, PG_* for postgres; see application-{mariadb,postgres}.yml). Reused
  for both the container env var NAME and the Secret key it reads from, so
  the two always match.
*/}}
{{- define "lilac.db.urlEnvName" -}}
{{- if eq .Values.backend.plannerDb "postgres" -}}PG_URL{{- else -}}MARIADB_URL{{- end -}}
{{- end -}}
{{- define "lilac.db.userEnvName" -}}
{{- if eq .Values.backend.plannerDb "postgres" -}}PG_USER{{- else -}}MARIADB_USER{{- end -}}
{{- end -}}
{{- define "lilac.db.passwordEnvName" -}}
{{- if eq .Values.backend.plannerDb "postgres" -}}PG_PASSWORD{{- else -}}MARIADB_PASSWORD{{- end -}}
{{- end -}}
