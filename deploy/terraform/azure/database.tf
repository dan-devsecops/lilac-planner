# Strong, generated admin password - never stored in code or tfvars.
resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%*-_=+"
}

# Azure Database for PostgreSQL Flexible Server - zone-redundant HA, private
# access via the delegated subnet. The app's Postgres storage profile connects
# to this natively (org.postgresql.Driver + Hibernate PostgreSQLDialect),
# unlike the mariadb profile which needs real MariaDB-only DDL that Azure's
# MySQL Flexible Server (actual MySQL, not MariaDB - Azure retired managed
# MariaDB) can't parse.
resource "azurerm_postgresql_flexible_server" "this" {
  name                   = "${var.prefix}-pg-${random_string.suffix.result}"
  resource_group_name    = azurerm_resource_group.this.name
  location               = var.location
  administrator_login    = var.db_admin_user
  administrator_password = random_password.db.result
  version                = var.postgres_version
  sku_name               = var.postgres_sku
  zone                   = "1"

  delegated_subnet_id           = azurerm_subnet.postgres.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false

  storage_mb = var.postgres_storage_mb

  # Zone-redundant HA: a synchronous standby in a second AZ, automatic failover.
  # Requires a General Purpose or Memory Optimized SKU (not Burstable).
  high_availability {
    mode                      = "ZoneRedundant"
    standby_availability_zone = "2"
  }

  backup_retention_days = 7
  tags                  = var.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]
}

# Two logical databases on the same HA server instance.
resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = var.db_name
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_postgresql_flexible_server_database" "app2" {
  name      = var.db_name_2
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}
