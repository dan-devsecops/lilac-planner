resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%*-_=+"
}

resource "google_sql_database_instance" "this" {
  name             = "${var.prefix}-mysql"
  database_version = "MYSQL_8_0"
  region           = var.region

  depends_on = [google_service_networking_connection.sql]

  settings {
    tier = var.db_tier

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
    }

    backup_configuration {
      enabled = true
    }
  }

  deletion_protection = false
}

resource "google_sql_database" "app" {
  name     = var.db_name
  instance = google_sql_database_instance.this.name
}

resource "google_sql_user" "app" {
  name     = var.db_user
  instance = google_sql_database_instance.this.name
  host     = "%"
  password = random_password.db.result
}
