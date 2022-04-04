from enum import Enum


class Pizza:

    def __init__(self, big: bool, cheese: str, meat: str):
        self.big = big
        self.cheese = cheese
        self.meat = meat

    def __str__(self):
        return "Pizza big=%s, " \
               "cheese=%s, " \
               "meat=%s" \
               % (self.big, self.cheese, self.meat)


class Receipt:

    def __init__(self, big: bool, cheese: str, meat: str):
        self.big = big
        self.cheese = cheese
        self.meat = meat

    def get_price(self) -> float:
        base_price = 2 if self.big is True else 1
        cheese_price = 0 if self.cheese is None else 1
        meat_price = 0 if self.meat is None else 1
        return base_price + cheese_price + meat_price


class Builder:

    def set_big(self, big: bool):
        pass

    def set_cheese(self, cheese: str):
        pass

    def set_meat(self, meat: str):
        pass


class PizzaBuilder(Builder):

    def __init__(self):
        self.big = None
        self.cheese = None
        self.meat = None

    def set_big(self, big: bool):
        self.big = big

    def set_cheese(self, cheese: str):
        self.cheese = cheese

    def set_meat(self, meat: str):
        self.meat = meat

    def get_result(self) -> Pizza:
        return Pizza(self.big, self.cheese, self.meat)


class ReceiptBuilder(Builder):

    def __init__(self):
        self.big = None
        self.cheese = None
        self.meat = None

    def set_big(self, big: bool):
        self.big = big

    def set_cheese(self, cheese: str):
        self.cheese = cheese

    def set_meat(self, meat: str):
        self.meat = meat

    def get_result(self) -> Receipt:
        return Receipt(self.big, self.cheese, self.meat)


class PizzaType(Enum):
    BIG_NO_CHEESE = 1
    SMALL_NO_MEAT = 2


class Director:

    def construct(self, pizza_type: PizzaType, builder: Builder):
        if pizza_type == PizzaType.BIG_NO_CHEESE:
            builder.set_big(True)
            builder.set_meat('ham')
        elif pizza_type == PizzaType.SMALL_NO_MEAT:
            builder.set_big(False)
            builder.set_cheese('mozzarella')


if __name__ == '__main__':
    director = Director()

    pizza_builder = PizzaBuilder()
    director.construct(PizzaType.BIG_NO_CHEESE, pizza_builder)
    big_no_cheese_pizza = pizza_builder.get_result()
    receipt_builder = ReceiptBuilder()
    director.construct(PizzaType.BIG_NO_CHEESE, receipt_builder)
    receipt_for_big_no_cheese_pizza = receipt_builder.get_result()
    print("Price is %s for %s" % (receipt_for_big_no_cheese_pizza.get_price(), big_no_cheese_pizza))

    pizza_builder = PizzaBuilder()
    director.construct(PizzaType.SMALL_NO_MEAT, pizza_builder)
    small_no_meat_pizza = pizza_builder.get_result()
    receipt_builder = ReceiptBuilder()
    director.construct(PizzaType.SMALL_NO_MEAT, receipt_builder)
    receipt_for_small_no_meat_pizza = receipt_builder.get_result()
    print("Price is %s for %s" % (receipt_for_small_no_meat_pizza.get_price(), small_no_meat_pizza))
