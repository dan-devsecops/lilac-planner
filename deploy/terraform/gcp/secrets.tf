# DB credentials in Secret Manager. The CD pipeline reads these to build the
# Kubernetes Secret - they never live in git or GitHub.
resource "google_secret_manager_secret" "db" {
  for_each  = local.db_secrets
  secret_id = "${var.prefix}-${each.key}"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db" {
  for_each    = local.db_secrets
  secret      = google_secret_manager_secret.db[each.key].id
  secret_data = each.value
}

locals {
  db_secrets = {
    "mariadb-user"     = var.db_user
    "mariadb-password" = random_password.db.result
    # Cloud SQL private IP; MariaDB driver connects to MySQL.
    "mariadb-url" = "jdbc:mariadb://${google_sql_database_instance.this.private_ip_address}:3306/${var.db_name}"
  }
}
