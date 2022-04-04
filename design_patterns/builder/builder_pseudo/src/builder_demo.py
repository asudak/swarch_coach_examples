# Pizza product to be assembled
# by the corresponding builder
class Pizza:

    big: bool
    cheese: str
    meat: str

    def __init__(self,
        big: bool,
        cheese: str,
        meat: str
    ):
        self.big = big
        self.cheese = cheese
        self.meat = meat

    # Represents assembled pizza as string
    def __str__(self):
        return "Pizza big=%s, " \
               "cheese=%s, " \
               "meat=%s" \
            % (self.big, self.cheese, self.meat)

# Receipt product to be assembled
# by the corresponding builder
class Receipt:

    big: bool
    cheese: str
    meat: str

    def __init__(self,
        big: bool,
        cheese: str,
        meat: str
    ):
        self.big = big
        self.cheese = cheese
        self.meat = meat

    # Calculate the price of the pizza
    def getPrice(self) -> float:
        basePrice = \
            2 if self.big is True \
            else 1
        cheesePrice = \
            0 if self.cheese is None \
            else 1
        meatPrice = \
            0 if self.meat is None \
            else 1
        return basePrice \
               + cheesePrice \
               + meatPrice

# Builder abstraction. Child classes
# are expected to implement the methods
# specified int this class.
class Builder:

    def big(self, big: bool):
        pass

    def cheese(self, cheese: str):
        pass

    def meat(self, meat: str):
        pass

# Builds Pizza product putting together
# provided information
class PizzaBuilder(Builder):

    big: bool
    cheese: str
    meat: str

    def big(self, big: bool):
        self.big = big

    def cheese(self, cheese: str):
        self.cheese = cheese

    def meat(self, meat: str):
        self.meat = meat

    def getResult(self) -> Pizza:
        return Pizza(
            self.big or None,
            self.cheese or None,
            self.meat or None
        )

# Builds Receipt product putting together
# provided information
class ReceiptBuilder(Builder):

    big: bool
    cheese: str
    meat: str

    def big(self, big: bool):
        self.big = big

    def cheese(self, cheese: str):
        self.cheese = cheese

    def meat(self, meat: str):
        self.meat = meat

    def getResult(self) -> Receipt:
        return Receipt(
            self.big or None,
            self.cheese or None,
            self.meat or None
        )

# Initiates construction of the desired
# product type. Concrete builder instantiates
# corresponding class of the product
# (e.g. Pizza or Receipt)
class Director:

    def construct(self, type, builder: Builder):
        if type == 'big-no-cheese':
            # This type doesn't consider
            # cheese in its configuration
            builder.big(True)
            builder.meat('ham')
        elif type == 'small-no-meat':
            # This type doesn't consider
            # meat in its configuration
            builder.big(False)
            builder.cheese('mozzarella')

# This is a Python-based pseudocode
# written with the intent to provide
# intuition about the Builder pattern.
# It is not a real runnable code
if __name__ == '__main__':
    director = Director()

    pizzaBuilder = PizzaBuilder()
    director.construct(
        'big-no-cheese',
        pizzaBuilder
    )
    bigNoCheesePizza = pizzaBuilder.getResult()
    receiptBuilder = ReceiptBuilder()
    director.construct(
        'big-no-cheese',
        receiptBuilder
    )
    receiptForBigNoCheesePizza = \
        receiptBuilder.getResult()
    print("Price is %s for %s" % (
        receiptForBigNoCheesePizza.getPrice(),
        bigNoCheesePizza
    ))

    pizzaBuilder = PizzaBuilder()
    director.construct(
        'small-no-meat',
        pizzaBuilder
    )
    smallNoMeatPizza = pizzaBuilder.getResult()
    receiptBuilder = ReceiptBuilder()
    director.construct(
        'small-no-meat',
        receiptBuilder
    )
    receiptForSmallNoMeatPizza = \
        receiptBuilder.getResult()
    print("Price is %s for %s" % (
        receiptForSmallNoMeatPizza.getPrice(),
        smallNoMeatPizza
    ))
