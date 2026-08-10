resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%*-_=+"
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.prefix}-db"
  subnet_ids = module.vpc.private_subnets
}

# Allow MariaDB traffic from within the VPC (the EKS nodes).
resource "aws_security_group" "rds" {
  name        = "${var.prefix}-rds"
  description = "MariaDB access from the cluster"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "MariaDB from VPC"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.prefix}-mariadb"
  engine         = "mariadb"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_admin_user
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az                = false
  backup_retention_period = 7
  skip_final_snapshot     = true
  deletion_protection     = false
}
