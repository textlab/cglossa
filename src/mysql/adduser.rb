#!/usr/bin/env ruby

require "scrypt"
require "mysql2"

user = ENV["GLOSSA_DB_USER"] || "glossa"
password = ENV["GLOSSA_DB_PASSWORD"]
prefix = ENV["GLOSSA_PREFIX"] || "glossa"
db = "#{prefix}__core"

client = Mysql2::Client.new(:host => "localhost", :username => user, 
                            :password => password, :database => db)

print "E-mail: "
email = gets.strip

print "Full name: "
name = gets.strip

print "Password: "
password = gets.strip

encrypted = SCrypt::Password.create(password)

statement = client.prepare(
  "insert into user set mail = ?, displayName = ?, password = ?")
statement.execute(email, name, encrypted)
