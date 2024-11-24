<!-- This is a template for the README.md that you should submit. For instructions on how to get started, see INSTRUCTIONS.md -->
# Design Choices

<!-- Write suggestions for improving the design of the tables here -->

The tables are designed rather well with not much redundant data, however, gig_ticket should have had its own ID which can be used as a foreign key by the ticket table since a ticket requires that there is a gig_ticket for a given ticket. The standardfee attribute in the act table is also seemingly redundant as it is rarely used and acts already have an actgigfee which they charge in act_gig. These two values are not required to be the same in addition.

# Task Implementations

<!-- For each of the tasks below, please write around 100-200 words explaining how your solution works (describing the behaviour of your SQL statements/queries) -->

## Task 1

Task 1 is handled by a single query which joins act and act_gig on actID to get the actname, ontime and finish time for a particular gigid. The finish time is calculated by converting an act's duration to an interval and adding it to the start time. The result is ordered by ascending ontime so that the first act in the gig is the first one in the result. The rest of the method is conversion of the result set to a 2d string array.

## Task 2

Task 2 initially gets the venueID associated with the given venuename, as well as the maximum value of gigID in the gig table in two select statements. This is so that we know the next gigID to be added sequentially (which is the max + 1). It then has 3 insert statements which insert the appropriate values to gig, act_gig and gig_ticket from the arguments.

## Task 3

Task 3 uses a simple insert statement to insert the appropriate values passed to the method. If the gigid references a gig which doesn't exist or a ticket type that doesn't exist for that gig, the update will not occur due to the rules in the schema, and the database will be rolled back to before the method was called

## Task 4

Task 4 gets the actID associated with the actname, then every act in that gig. It iterates through those acts and uses the DetermineHeadliner plpgsql function to determine if the whole gig should be cancelled or not. If the act is not a headliner then it attempts to delete the act from the gig, however if this goes wrong (violates a constraint) then the whole gig is cancelled. If the act is a headliner then all tickets for that gig are set to 0, the gigstatus is set to 'Cancelled' and then we get every distinct customeremail from the ticket table which was associated to that gig. We get distinct customeremails as a customer can have multiple tickets for that gig, and we don't need them to show up twice in the output.

## Task 5

Task 5 gets all gigIDs in ascending order, then passes that to the plpgsql function CalculateRemaningCostOfGig which takes a gigID and returns how many tickets the given gig needs to sell. This is then added to the return string, formatted correctly and then output. The function gets the sum of actgigfees in the given gig, the venue hirecost for it, the sum of all tickets sold and the price of an adult ticket. It then adds the costs, subtracts the tickets sold and divides by the price of an adult ticket.

## Task 6

Task 6 uses 2 nested CTEs. The innermost query gets the actname, actID, ontime year and a count from act_gig, ticket and act if and only if the act is a headliner and the gig is going ahead. The count() counts all distinct ticketIDs grouped by actid, year and actname (so it counts how many tickets each act sold in a given year where that act was a headliner). The outer CTE creates the total rows by summing the count column, and grouping by the rollup function of actname and year. This also creates a row at the end of the results which includes the total of all counts in the inner subquery. The final part of the query creates an extra column in the table which corresponds to the rollup value of each act for each act's row. This is so that we can sort by them to match the sample output. The sum part uses "CASE WHEN" which is essentially a WHERE for an aggregate function. This one looks for when to_char is null (which indicates that row is a total row) and does not include it in the sum calculation so the total column matches the total row.

## Task 7

The query for task 7 uses 3 CTEs nested within each other. The innermost query (v) gets the actname, actID, gigID, and ontime year from act_gig joined with act, if and only if the act is a headliner, ordered by increasing actID and decreasing year. Count represents the number of years a certain act has been the headliner. The query outside it (w) left outer joins v with ticket on a matching gigid, and projects the same attributes, as well as the customername of each ticket and the number of times each customer has bought a ticket for each act. The query outside that (u) projects the same attributes, but only if the number of tickets a customer has bought for an act >= the number of years that act has headlined, or customer name is null. Customername being null indicates that the act has been a headliner but no one has gone to every show. u is then ordered by the number of tickets each customer has bought for each act in descending order. The outermost query selects the distinct actnames and customernames from u and orders them by ascending actname which matches the output to the sample output.

## Task 8

Task 8 is made up of two queries. The subquery gets the venuename, actname, NumTickets() and capacity from venue cross joined with act. The outer query only displays the results from the inner query where the number of tickets is less than the capacity, ordered by venuename in alphabetical order and NumTickets in descending order. NumTickets is a function which takes an actID and a venueID and calculates how many tickets would need to be sold for that venue to break even if they hired only that act. It does this by getting the hirecost of that venue, the standard fee of the given act, and the average ticket cost of every gig that is not cancelled. It then sums the standard fee with the hirecost and divides it by the average ticket cost to get the number of tickets to break even.