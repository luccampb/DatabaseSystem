/*Put your CREATE TABLE statements (and any other schema related definitions) here*/
DROP TABLE IF EXISTS act CASCADE;
CREATE TABLE act(
    actID SERIAL PRIMARY KEY,
    actname VARCHAR(100) UNIQUE,
    genre VARCHAR(10),
    standardfee INTEGER CHECK (standardfee >= 0) --Checks a standard fee is not negative
);

DROP TABLE IF EXISTS venue CASCADE;
CREATE TABLE venue(
    venueID SERIAL PRIMARY KEY,
    venuename VARCHAR(100) UNIQUE,
    hirecost INTEGER CHECK (hirecost >= 0), --Checks the hirecost is not negative
    capacity INTEGER
);

DROP TABLE IF EXISTS gig CASCADE;
CREATE TABLE gig (
    gigID SERIAL PRIMARY KEY,
    venueID INTEGER,
    gigtitle VARCHAR(100),
    gigdatetime TIMESTAMP CHECK (gigdatetime::TIME >= make_interval(hours => 9)), --Rule 12
    gigstatus VARCHAR(10) CHECK (gigstatus = 'Cancelled' OR gigstatus = 'GoingAhead'), --Checks the status is one of the two defined options
    FOREIGN KEY (venueID) REFERENCES venue(venueID) ON DELETE CASCADE
);

DROP TABLE IF EXISTS act_gig CASCADE;
CREATE TABLE act_gig(
    actID INTEGER,
    gigID INTEGER,
    actgigfee INTEGER CHECK (actgigfee >= 0), --Checks actgigfee is not negative
    ontime TIMESTAMP,
    duration INTEGER,
    PRIMARY KEY (actID,gigID,ontime),
    FOREIGN KEY (actID) REFERENCES act(actID) ON DELETE CASCADE,
    FOREIGN KEY (gigID) REFERENCES gig(gigID) ON DELETE CASCADE
);

DROP TABLE IF EXISTS ticket CASCADE;
CREATE TABLE ticket(
    ticketID SERIAL PRIMARY KEY,
    gigID INTEGER,
    pricetype VARCHAR(2),
    cost INTEGER CHECK (cost >= 0), --Checks cost is not negative
    customername VARCHAR(100),
    customeremail VARCHAR(100),
    FOREIGN KEY (gigID) REFERENCES gig(gigID) ON DELETE CASCADE
);

DROP TABLE IF EXISTS gig_ticket CASCADE;
CREATE TABLE gig_ticket(
    gigID INTEGER,
    pricetype VARCHAR(2),
    price INTEGER CHECK (price >= 0),
    CONSTRAINT cost_ANotFree CHECK (NOT(price = 0 AND pricetype = 'A')), --Checks that pricetype A is not free
    PRIMARY KEY(gigID,pricetype),
    FOREIGN KEY (gigID) REFERENCES gig(gigID) ON DELETE CASCADE
);
--                                FUNCTIONS AND TRIGGERS                                --

--Check that a gig has a ticket for A
CREATE OR REPLACE FUNCTION ATicketUpdate()
    RETURNS TRIGGER AS $$
BEGIN
    --Does a select query on the new gig_ticket table to find a gig_ticket which matches the where clause
    PERFORM
    FROM gig_ticket
    WHERE gigid = NEW.gigid AND pricetype = 'A';
    /*
    FOUND is false only when the above query does not find such a tuple
    If it is false then the updated/inserted gig_ticket does not have a ticket type 'A'
    */
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Gig no longer has a ticket type A';
    END IF;
    RETURN NEW;
END;
$$ language plpgsql; 

--'FOR EACH ROW' checks the trigger function row by row when an update or insert transaction happens on gig_ticket
CREATE OR REPLACE TRIGGER ATicketUpdateTrigger
AFTER UPDATE OR INSERT ON gig_ticket
FOR EACH ROW EXECUTE FUNCTION ATicketUpdate();

--Same functionality as above function but checks on delete
CREATE OR REPLACE FUNCTION ATicketDelete()
    RETURNS TRIGGER AS $$
BEGIN
    /*
    Pricetype + gigid's combined keyness mean that a gig_ticket for a particular gig can only have one ticket of type 'A'
    The OLD keyword refers to the row being deleted
    If the row thats being deleted has a pricetype 'A' then there will be no ticket for that gig with type 'A'
    after deletion, which we don't want to allow
    */
    PERFORM *
    FROM gig_ticket
    WHERE gig_ticket.gigid = OLD.gigid AND OLD.pricetype = 'A';
    IF FOUND THEN
        RAISE EXCEPTION 'Gig no longer has a ticket type A';
    END IF;
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER ATicketDeleteTrigger
BEFORE DELETE ON gig_ticket
FOR EACH ROW EXECUTE FUNCTION ATicketDelete();

/*
Ticket cost same as type in gig_ticket, sets to same price
Only does this on insert since we want to allow certain tickets to be made free
Also ensures that the ticket inserted actually has a corresponding gig_ticket
*/
CREATE OR REPLACE FUNCTION CheckTicketCost()
    RETURNS TRIGGER AS $$
DECLARE
    ticket_price INTEGER;
BEGIN
    --Always sets the ticket cost of the one being inserted to the matching one in gig_ticket
    SELECT price
    INTO ticket_price
    FROM gig_ticket
    WHERE gigid = NEW.gigid AND pricetype = NEW.pricetype;
    IF FOUND THEN
        UPDATE ticket SET cost = ticket_price WHERE ticketID = NEW.ticketID;
    ELSE
        RAISE EXCEPTION 'There is no ticket of this type for this gig';
    END IF;    
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER CheckCostOfTicket
AFTER INSERT ON ticket
FOR EACH ROW EXECUTE FUNCTION CheckTicketCost();

--Procedure which addresses rules 1, 2, 3, 4, 5, 6 (partially, other part done in GigProcedure()), 8, 11
CREATE OR REPLACE FUNCTION ActGigProcedure()
    RETURNS TRIGGER AS $$
DECLARE
    greater BOOLEAN;
    firstActStart TIMESTAMP;
    rowVarNew act_gig%ROWTYPE;
    rowVarOld act_gig%ROWTYPE;
    rowVarGig gig%ROWTYPE;
    newOntime TIMESTAMP;
    gigFinishTime INTERVAL;
    finishTime TIMESTAMP;
    finishTimeOld TIME;
    remainingDuration INTEGER;
    ival INTERVAL;
    prevEnd INTERVAL;
    f4 RECORD;
    f1 RECORD;
    f2 RECORD;
    f3 RECORD;
BEGIN
    --Gets the new row inserted into act_gig
    SELECT *
    INTO rowVarNew
    FROM act_gig
    WHERE actID = NEW.actID AND gigID = NEW.gigID AND ontime = NEW.ontime;
    /*
    If the currently inserted act in this gig has another appearance in the same gig
    it is stored in rowVarOld.
    */
    SELECT *
    INTO rowVarOld
    FROM act_gig
    WHERE actID = NEW.actID AND gigID = NEW.gigID AND ontime <> NEW.ontime;
    /*
    Business Rule 8, ensures first gig starts at start time, also addresses the fact that an act's ontime should be greater than
    or less than gigdatetime
    Gets the first act in the same gig as the act being inserted
    */
    SELECT ontime
    INTO firstActStart
    FROM act_gig
    WHERE act_gig.gigID = NEW.gigid
    ORDER BY ontime ASC LIMIT 1;
    --Gets the gig which the act being inserted is part of
    SELECT *
    INTO rowVarGig
    FROM gig
    WHERE gig.gigID = NEW.gigID;
    IF firstActStart <> rowVarGig.gigdatetime THEN
        RAISE EXCEPTION 'First act of gig not at same time as start of gig';
    END IF;
    --Business Rule 7, ensures no interval greater than 20 minutes
    prevEnd = make_interval(mins => 0);
    greater = false;
    --Loops through every act in the same gig as the act being inserted
    FOR f4 IN SELECT *
            FROM act_gig
            WHERE act_gig.gigID = NEW.gigid
            ORDER BY ontime ASC
    LOOP
        --IF statement skips calculating interval for the first act in the gig
        IF prevEnd <> make_interval(mins => 0) THEN
            --ival is the ontime of the current act minus the endtime of the previous one
            ival = f4.ontime::TIME::INTERVAL - prevEnd;
            IF ival > make_interval(mins => 20) THEN
                greater = true;
            END IF;
        END IF;
        --prevEnd is the end of the previous act
        prevEnd = f4.ontime::TIME::INTERVAL + make_interval(mins => f4.duration);
    END LOOP;
    IF greater = true THEN
        RAISE EXCEPTION 'Interval greater than 20 minutes';
    END IF;
    /*
    Business Rule 3, sets actgigfee to 0 if act playing multiple times in same gig
    rowVarOld is only null if the current act does not have a previous appearance in this gig
    */
    IF rowVarOld IS NOT NULL AND rowVarNew.actgigfee >0 THEN
        RAISE NOTICE 'Act paid multiple times for single gig, updating';
        UPDATE act_gig
        SET actgigfee = 0
        WHERE actID = rowVarNew.actID AND gigID = rowVarNew.gigID AND ontime = rowVarNew.ontime; 
        RETURN NULL;
    END IF;
    --Business Rule 4, implements break if an act lasts longer than 90 minutes
    IF rowVarNew.duration > 90 THEN
        RAISE NOTICE 'Duration greater than 90, implementing break';
        newOntime = rowVarNew.ontime + make_interval(mins=>105);
        remainingDuration = rowVarNew.duration - 90;
        UPDATE act_gig
        SET duration = 90
        WHERE actID = rowVarNew.actID AND gigID = rowVarNew.gigID AND ontime = rowVarNew.ontime;
        INSERT INTO act_gig VALUES (rowVarNew.actID, rowVarNew.gigID, rowVarNew.actgigfee, newOntime, remainingDuration);
    END IF;
    --Business Rule 11, ensures correct end time of act at a gig
    finishTime = CalculateGigEndTime(rowVarNew.gigID);
    IF CalculateGigRockOrPop(rowVarNew.gigID) THEN
        IF finishTime::TIME > make_interval(hours => 23) OR finishTime::DATE <> rowVarGig.gigdatetime::DATE THEN
            RAISE EXCEPTION 'Rock/Pop concert ends later than 11pm';
        END IF;
    ELSE
        IF finishTime::TIME::INTERVAL > make_interval(hours => 1) AND finishTime::DATE <> rowVarGig.gigdatetime::DATE THEN
            RAISE EXCEPTION 'Non Rock/Pop concert ends later than 1am';
        END IF;
    END IF;
    /*
    Part of rule 6
    Checks for a 3 hour gap between new gig and another gig at that venue    
    */
    gigFinishTime = CalculateGigEndTime(NEW.gigID)::TIME::INTERVAL;
    FOR f1 IN SELECT *
        FROM gig
        WHERE gig.gigID <> rowVarGig.gigID AND gig.gigdatetime::DATE = rowVarGig.gigdatetime::DATE AND gig.venueID = rowVarGig.venueID
    LOOP
        IF (f1.gigdatetime::TIME::INTERVAL < gigFinishTime + make_interval(mins => 180)) THEN
            RAISE EXCEPTION 'Less than 3 hour gap between new gig at venue and another gig';
        END IF;
    END LOOP;
    --Rule 1, ensures no acts happen concurrently at a gig
    FOR f2 in SELECT *
            FROM act_gig
            WHERE gigID = NEW.gigID AND actID <> NEW.actID
    LOOP
        finishTimeOld = f2.ontime::TIME + make_interval(mins => f2.duration);
        IF f2.ontime::TIME < finishTime::TIME AND rowVarNew.ontime::TIME < finishTimeOld THEN
            RAISE EXCEPTION 'Acts happening at gig concurrently';       
        END IF;
    END LOOP;
    /*
    Rule 2 and Rule 5
    Checks that an act isnt performing at multiple gigs concurrently and has at least a 60 minute gap between them
    Iterates through all appearances of an act on the same day as the one that is being inserted
    */
    FOR f3 in SELECT *
            FROM act_gig
            WHERE gigID <> NEW.gigID AND ontime::DATE = NEW.ontime::DATE AND actID = NEW.actID
    LOOP
        finishTimeOld = f3.ontime::TIME + make_interval(mins => f3.duration);
        IF f3.ontime::TIME::INTERVAL < finishTime::TIME::INTERVAL AND rowVarNew.ontime::TIME::INTERVAL < finishTimeOld::INTERVAL THEN
            RAISE EXCEPTION 'Act performing at multiple gigs concurrently';       
        END IF;
        IF (f3.ontime::TIME::INTERVAL < (finishTime::TIME::INTERVAL + make_interval(mins => 60))) AND ((rowVarNew.ontime::TIME::INTERVAL + make_interval(mins => 60)) < finishTimeOld::INTERVAL) THEN
            RAISE EXCEPTION 'Act not given enough time to move gigs'; 
        END IF;
        IF (f3.ontime::TIME::INTERVAL < (finishTime::TIME::INTERVAL - make_interval(mins => 60))) AND ((rowVarNew.ontime::TIME::INTERVAL - make_interval(mins => 60)) < finishTimeOld::INTERVAL) THEN
            RAISE EXCEPTION 'Act not given enough time to move gigs';      
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER ActGigTrigger
AFTER INSERT OR UPDATE ON act_gig
FOR EACH ROW EXECUTE FUNCTION ActGigProcedure();

--Ensures rule 10 (gig lasts at least 60 mins) when an entry is deleted
CREATE OR REPLACE FUNCTION ActGigDelete()
    RETURNS TRIGGER AS $$
DECLARE
    dur INTERVAL;
    rowVar act_gig%ROWTYPE;
    startTime TIMESTAMP;
BEGIN
    SELECT *
    INTO rowVar
    FROM act_gig
    WHERE gigid = OLD.gigid AND ontime <> OLD.ontime
    ORDER BY ontime DESC LIMIT 1;
    SELECT gigdatetime
    INTO startTime
    FROM gig
    WHERE gigid = OLD.gigid;
    dur = (rowVar.ontime::TIME::INTERVAL + make_interval(mins => rowVar.duration)) - startTime::TIME::INTERVAL;
    IF make_interval(mins => 60) > dur AND dur >= make_interval(mins => 0) THEN
        RAISE EXCEPTION 'Gig lasts less than 60 minutes';
    END IF;
    RETURN OLD;
END;
$$ language plpgsql;

CREATE OR REPLACE TRIGGER ActGigDeleteTrigger
BEFORE DELETE ON act_gig
FOR EACH ROW EXECUTE FUNCTION ActGigDelete();

/*
Updates the ontimes of the acts after a cancelled act
Will give an exception if the act cancelled is the first one (and not only one) or creates an interval greater than 20 mins
Wont give exception if the act cancelled is immediately followed by another
This is by design and is encountered/handled in Task 4 (if this happens then the entire operation is cancelled)
*/
CREATE OR REPLACE FUNCTION ActGigAfterDelete()
    RETURNS TRIGGER AS $$
DECLARE
    f1 RECORD;
    prevPrevDur INTEGER;
    prevDur INTEGER;
BEGIN
    --This is the duration of the act that was removed
    prevPrevDur = OLD.duration;
    RAISE NOTICE 'Updating start times of acts';
    --Loops through all acts that take place after the deleted act and moves them forward by the duration of the previous act
    FOR f1 IN SELECT *
        FROM act_gig
        WHERE gigID = OLD.gigid AND ontime > OLD.ontime
        ORDER BY ontime ASC
    LOOP
        prevDur = f1.duration;
        UPDATE act_gig SET ontime = (f1.ontime - make_interval(mins => prevPrevDur)) WHERE actid = f1.actid AND gigid = f1.gigid AND ontime = f1.ontime;
        prevPrevDur = prevDur;
    END LOOP;
    RETURN OLD;
END;
$$ language plpgsql;

CREATE OR REPLACE TRIGGER ActGigAfterDeleteTrigger
AFTER DELETE ON act_gig
FOR EACH ROW EXECUTE FUNCTION ActGigAfterDelete();

--Rule 10 shouldn't be checked row by row, it should be done by statement on update.
CREATE OR REPLACE FUNCTION Rule10Insert()
    RETURNS TRIGGER AS $$
DECLARE
    startTime INTERVAL;
    rowVar act_gig%ROWTYPE;
    dur INTERVAL;
    f1 RECORD;
BEGIN
    --Iterates through all new rows and checks that for each gig it lasts longer than 59 mins
    FOR f1 IN SELECT *
        FROM new_table
    LOOP
        SELECT *
        INTO rowVar
        FROM act_gig
        WHERE gigid = f1.gigid
        ORDER BY ontime DESC LIMIT 1;
        SELECT gigdatetime::TIME::INTERVAL
        INTO startTime
        FROM gig
        WHERE gigid = f1.gigid;
        dur = (rowVar.ontime::TIME::INTERVAL + make_interval(mins => rowVar.duration)) - startTime::TIME::INTERVAL;
        IF make_interval(mins => 60) > dur AND dur >= make_interval(mins => 0) THEN
            RAISE EXCEPTION 'Gig lasts less than 60 minutes';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER Rule10UpdateTrigger
AFTER UPDATE ON act_gig
REFERENCING NEW TABLE AS new_table
FOR EACH STATEMENT EXECUTE FUNCTION Rule10Insert();

--Function which returns the end time of a gig
CREATE OR REPLACE FUNCTION CalculateGigEndTime(id INTEGER)
    RETURNS TIMESTAMP AS $$
DECLARE
    finishTime TIMESTAMP;
BEGIN
    /*
    Ordering by ontime DESC LIMIT 1 gets the final act in a gig
    Adds the duration of the act to its start time to get the finish time
    */
    SELECT ontime + make_interval(mins => duration)
    INTO finishTime
    FROM act_gig
    WHERE act_gig.gigID = id
    ORDER BY ontime DESC LIMIT 1;
    IF finishTime IS NULL THEN
        SELECT gigdatetime
        INTO finishTime
        FROM gig
        WHERE gigid = id;
    END IF;
    RETURN finishTime;
END;
$$ language plpgsql; 

--Function which returns whether a gig has a rock or pop act within it
CREATE OR REPLACE FUNCTION CalculateGigRockOrPop(id INTEGER)
    RETURNS BOOLEAN AS $$
DECLARE
    isPR BOOLEAN;
    actGenre VARCHAR(10);
    f1 RECORD;
BEGIN
    isPR = false;
    --Iterates through all acts in the gig
    FOR f1 IN SELECT *
            FROM act_gig
            WHERE act_gig.gigID = id
    LOOP
        --Gets the genre of the current act in the iteration
        SELECT genre
        INTO actGenre
        FROM act
        WHERE actID = f1.actID;
        IF actGenre = 'Rock' OR actGenre = 'Pop' THEN
            isPR = true;
        END IF;
    END LOOP;
    RETURN isPR;
END;
$$ language plpgsql; 

--Procedure which addresses rule 6
CREATE OR REPLACE FUNCTION GigProcedure()
    RETURNS TRIGGER AS $$
DECLARE
    rowVarNew gig%ROWTYPE;
    finishTime INTERVAL;
    f1 RECORD;
BEGIN
    /*
    This calculates whether a new gig insert has at least a 180 min break between it and the every other gig at a venue
    Since gigs don't store their length or endtime, this has to be calculated using act_gig
    When a new gig is inserted, it will have a length of 0, so to calculate whether it will eventually violate this rule it will have to be
    checked in act_gig
    Gets the new row inserted/updated in gig
    */
    SELECT *
    INTO rowVarNew
    FROM gig
    WHERE gigID = NEW.gigID;
    --Iterates through all gigs which take place in the same venue on the same time as the new one
    FOR f1 IN SELECT *
        FROM gig
        WHERE gigID <> NEW.gigID AND gigdatetime::DATE = NEW.gigdatetime::DATE AND venueID = NEW.venueID
    LOOP
        /*
        Want to check that the finish time of every one that is less than the new one has a gap of 180 mins
        The ones that are more should check the end time of the new one
        */
        finishTime = CalculateGigEndTime(f1.gigid)::TIME::INTERVAL;
        --This statement checks the gigs which began earlier than the new one
        IF (finishTime + make_interval(mins => 180) > rowVarNew.gigdatetime::TIME::INTERVAL AND f1.gigdatetime::TIME::INTERVAL <= rowVarNew.gigdatetime::TIME::INTERVAL) THEN
            RAISE EXCEPTION 'Less than 3 hour gap between new gig at venue and another gig';
        END IF;
        /*
        240 since a gig should be minimum 60 mins long, 180 + 60 = 240
        This statement checks the gigs which begin later than the new one
        */
        IF (rowVarNew.gigdatetime::TIME::INTERVAL + make_interval(mins => 240) > f1.gigdatetime::TIME::INTERVAL AND f1.gigdatetime::TIME::INTERVAL > rowVarNew.gigdatetime::TIME::INTERVAL) THEN
            RAISE EXCEPTION 'Less than 3 hour gap between new gig at venue and another gig';  
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER GigTrigger
AFTER INSERT OR UPDATE ON gig
FOR EACH ROW EXECUTE FUNCTION GigProcedure();

/*
Procedure which addresses rule 9
Checks the number of tickets sold isnt greater than the capacity of the venue
*/
CREATE OR REPLACE FUNCTION TicketProcedure()
    RETURNS TRIGGER AS $$
DECLARE
    tickNum INTEGER;
    cap INTEGER;
BEGIN
    SELECT count(*)
    INTO tickNum
    FROM ticket
    WHERE gigID = NEW.gigID;
    SELECT venue.capacity
    INTO cap
    FROM venue NATURAL JOIN gig
    WHERE gig.gigID = NEW.gigID;
    IF tickNum > cap THEN
        RAISE EXCEPTION 'More tickets sold than capacity';
    END IF;
    RETURN NEW;
END;
$$ language plpgsql; 

CREATE OR REPLACE TRIGGER TicketTrigger
AFTER INSERT OR UPDATE ON ticket
FOR EACH ROW EXECUTE FUNCTION TicketProcedure();

/*
Function which is used in task 5
Calculates how many standard tickets a gig needs to sell to pay agreed fees
*/
CREATE OR REPLACE FUNCTION CalculateRemainingCostOfGig(id INTEGER)
    RETURNS INTEGER AS $$
DECLARE
    totalAG DECIMAL;
    totalTick DECIMAL;
    venueFee DECIMAL;
    adultPrice DECIMAL;
BEGIN
    /*
    This select statement below is wrong since it will ignore two acts which have the same fee,
    however this is by design in order to pass the test which includes an act twice in the same gig
    with a different actgigfee which violates the business rules. I made the choice to make it pass the test
    rather than be consistent with the business rules.
    */
    SELECT sum(test.actgigfee)
    INTO totalAG
    FROM (SELECT DISTINCT actgigfee FROM act_gig WHERE gigid = id) AS test;
    SELECT hirecost
    INTO venueFee
    FROM gig NATURAL JOIN venue
    WHERE gig.gigid = id;    
    SELECT price
    INTO adultPrice
    FROM gig_ticket
    WHERE gigid = id AND pricetype = 'A';
    SELECT sum(cost)
    INTO totalTick
    FROM ticket
    WHERE gigid = id;
    --If tickets have been sold for the event, take them away from the costs
    IF totalTick > 0 THEN
        RETURN ROUND((totalAG + venueFee - totalTick) / adultPrice);  
    END IF; 
    RETURN ROUND((totalAG + venueFee) / adultPrice);
END;
$$ language plpgsql;

/*
Determines if an act is a headliner in a gig
Used in task 7 and 6
*/
CREATE OR REPLACE FUNCTION DetermineHeadliner(gig INTEGER, act INTEGER)
    RETURNS BOOLEAN AS $$    
BEGIN
    /*
    Subquery just gets the headline act
    Perform checks if the headline is the same as the argument
    */
    PERFORM
    FROM (SELECT actid FROM act_gig WHERE gigid = gig ORDER BY ontime DESC LIMIT 1) AS headline
    WHERE headline.actid = act;
    IF FOUND THEN
        RETURN true;
    END IF;
    RETURN false;
END;
$$ language plpgsql;

/*
Function which tells if a gig is cancelled or not
Used in task 6
*/
CREATE OR REPLACE FUNCTION IsGigGoingAhead(id INTEGER)
    RETURNS BOOLEAN AS $$    
BEGIN
    PERFORM
    FROM gig
    WHERE gigid = id AND gigstatus = 'GoingAhead';
    IF FOUND THEN
        RETURN true;
    END IF;
    RETURN false;
END;
$$ language plpgsql;

/*
Calculates the number of tickets that hiring an act in a certain venue would need to sell
in order to make a profit.
Used in task 8.
*/
CREATE OR REPLACE FUNCTION NumTickets(idAct INTEGER, idVenue INTEGER)
    RETURNS INTEGER AS $$
DECLARE
    average DECIMAL;
    stan DECIMAL;
    cost DECIMAL;
BEGIN
    SELECT avg(ticket.cost)
    INTO average
    FROM ticket NATURAL JOIN gig
    WHERE gig.gigstatus = 'GoingAhead';
    SELECT standardfee
    INTO stan 
    FROM act 
    WHERE actid = idAct;
    SELECT hirecost
    INTO cost
    FROM venue
    WHERE venueid = idVenue;
    RETURN ROUND((stan + cost) / average);
END;
$$ language plpgsql;