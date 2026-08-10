# Customer-managed KMS key for the DB secret, instead of the default
# aws/secretsmanager key. Gives us automatic annual key rotation and an
# explicit access boundary (only principals granted kms:Decrypt can read it).
resource "aws_kms_key" "secrets" {
  description             = "${var.prefix} Secrets Manager CMK"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  tags                    = var.tags
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${var.prefix}-secrets"
  target_key_id = aws_kms_key.secrets.key_id
}

# DB credentials in Secrets Manager. The CD pipeline reads these to build the
# Kubernetes Secret - they never live in git or GitHub.
resource "aws_secretsmanager_secret" "db" {
  name = "${var.prefix}/db"
  # 7-day recovery window so an accidental delete is reversible. For a throwaway
  # dev stack you can still force an immediate delete:
  #   aws secretsmanager delete-secret --secret-id <arn> --force-delete-without-recovery
  recovery_window_in_days = 7
  kms_key_id              = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    MARIADB_USER     = var.db_admin_user
    MARIADB_PASSWORD = random_password.db.result
    MARIADB_URL      = "jdbc:mariadb://${aws_db_instance.this.address}:3306/${var.db_name}"
  })
}
