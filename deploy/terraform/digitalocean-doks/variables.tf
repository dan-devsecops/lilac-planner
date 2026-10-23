variable "do_token" {
  description = "DigitalOcean personal access token."
  type        = string
  sensitive   = true
}

variable "name_prefix" {
  description = "Name prefix for all resources."
  type        = string
  default     = "lilac-doks"
}

variable "region" {
  description = "DigitalOcean region slug."
  type        = string
  default     = "fra1"
}

variable "project_name" {
  description = "DO Projects display name in the console. Kept separate from the droplet module's project (../digitalocean) so the two deployments don't collide."
  type        = string
  default     = "Lilac Planner (Kubernetes)"
}

# ---------------- DOKS cluster ----------------

variable "kubernetes_version" {
  description = "DOKS control-plane version slug (e.g. \"1.31.1-do.4\"). Leave \"\" to use the latest patch of the newest supported minor."
  type        = string
  default     = ""
}

variable "node_size" {
  description = "Droplet size slug for worker nodes."
  type        = string
  default     = "s-2vcpu-2gb"
}

variable "node_count" {
  description = "Number of worker nodes. Ignored when autoscale = true."
  type        = number
  default     = 2
}

variable "autoscale" {
  description = "Enable the node pool autoscaler (scales between min_nodes and max_nodes instead of a fixed node_count)."
  type        = bool
  default     = false
}

variable "min_nodes" {
  description = "Autoscaler floor. Only used when autoscale = true."
  type        = number
  default     = 2
}

variable "max_nodes" {
  description = "Autoscaler ceiling. Only used when autoscale = true."
  type        = number
  default     = 4
}

variable "ha_control_plane" {
  description = "Highly-available control plane (extra ~$40/mo). Off by default - a single control plane is fine for a low-traffic app; it's the worker nodes that run your pods."
  type        = bool
  default     = false
}

# ---------------- Container registry (DOCR) ----------------

variable "create_registry" {
  description = "Create a DOCR registry. DO allows exactly ONE registry per account - if you already have one (e.g. from another project), set this to false and point registry_name at it instead."
  type        = bool
  default     = true
}

variable "registry_name" {
  description = "DOCR registry name. Must be globally unique across all of DigitalOcean, not just your account."
  type        = string
  default     = "lilac-planner"
}

variable "registry_tier" {
  description = "DOCR subscription tier slug (starter | basic | professional)."
  type        = string
  default     = "basic"
}

# ---------------- Managed database ----------------

variable "db_version" {
  # VERIFY against your account before trusting this default - engine
  # version strings have moved under us once already this project (see git
  # history: MySQL "8"/"8.0" were both rejected mid-2026, only "8.4" worked):
  #   doctl databases options versions --engine pg
  description = "PostgreSQL engine version."
  type        = string
  default     = "17"
}

variable "db_size" {
  description = "Managed DB node size slug. db-s-1vcpu-1gb is single-node ONLY - DO rejects db_node_count=2 on it with a 422 'invalid layout and size combination'. If you set db_node_count=2, bump this to at least db-s-1vcpu-2gb ($30.45/node/mo vs $15.15) - check doctl databases options slugs --engine pg -o json for which sizes support which node counts before changing either."
  type        = string
  default     = "db-s-1vcpu-1gb"
}

variable "db_node_count" {
  description = "1 = single node. 2 = primary + standby (HA) - requires db_size to be at least db-s-1vcpu-2gb, see db_size above. Not just 2x the single-node price: the per-node price also goes up with the required size bump (~$61/mo total, not ~$30)."
  type        = number
  default     = 1
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_user" {
  description = "Application DB user (not the cluster admin user)."
  type        = string
  default     = "planner"
}

# ---------------- Connection pool (PgBouncer, free - no extra node) ----------------

variable "enable_connection_pool" {
  description = "Create a PgBouncer connection pool in front of the primary cluster. No extra billable node - just a proxy DO runs alongside the cluster."
  type        = bool
  default     = true
}

variable "db_pool_mode" {
  description = "PgBouncer pool mode: session | transaction | statement. transaction is the usual default for web-app workloads (short-lived connections, no session state like prepared statements held open)."
  type        = string
  default     = "transaction"
}

variable "db_pool_size" {
  description = "Number of PgBouncer backend connections held open to the primary - i.e. how many actual Postgres connections the pool is allowed to use, regardless of how many client connections it's serving."
  type        = number
  default     = 10
}

# ---------------- Read replica (separate node, extra cost) ----------------

variable "enable_read_replica" {
  description = "Create a queryable read-only replica - a SEPARATE feature from db_node_count's HA standby above (that standby is for failover only, never queryable). Adds another billable node."
  type        = bool
  default     = false
}

variable "db_replica_size" {
  description = "Node size slug for the read replica. Must be the same size or LARGER than db_size (doctl databases replica create --help spells this out explicitly) - DO rejects a smaller replica. Can't actually be sized down for light reporting load, despite being a separate node."
  type        = string
  default     = "db-s-1vcpu-1gb"
}
