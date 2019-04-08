import slick.jdbc.PostgresProfile.api._

class Coffees(tag: Tag) extends Table[(String, Int)](tag, "coffees") {
  def name = column[String]("cof_name")
  def price = column[Int]("price")
  def * = (name, price)
}

object Coffees {
  val coffees = TableQuery[Coffees]
}
